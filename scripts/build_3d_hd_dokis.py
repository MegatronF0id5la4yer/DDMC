import os
import json
import math
import subprocess
import numpy as np
from PIL import Image
from scipy.ndimage import distance_transform_edt, gaussian_filter

ROOT = str(Path(__file__).resolve().parents[1])
BLENDER_EXE = r"C:\Program Files\Blender Foundation\Blender 5.2\blender.exe"
BLENDER_SCRIPT = os.path.join(ROOT, "scripts", "blender_rig_and_render_hd.py")
SCRATCH_DIR = os.path.join(ROOT, "build_3d_hd_tmp")

HD_CHARACTERS = [
    {
        "char_id": "monika_school",
        "doki_id": "monika",
        "rel_path": "monika escuela/Mon1.webp",
        "center_x_px": 475.0,
        "color_hex": "#34D399",
    },
    {
        "char_id": "sayori_school",
        "doki_id": "sayori",
        "rel_path": "sayori escuela/Say4.webp",
        "center_x_px": 496.0,
        "color_hex": "#FB7185",
    },
    {
        "char_id": "sayori_casual",
        "doki_id": "sayori",
        "rel_path": "sayori casual/Yori1.webp",
        "center_x_px": 498.0,
        "color_hex": "#FB7185",
    },
    {
        "char_id": "natsuki_school",
        "doki_id": "natsuki",
        "rel_path": "natsuki escuela/Schoola.webp",
        "center_x_px": 494.0,
        "color_hex": "#F472B6",
    },
    {
        "char_id": "natsuki_casual",
        "doki_id": "natsuki",
        "rel_path": "natsuki casual/Casual1.webp",
        "center_x_px": 494.0,
        "color_hex": "#F472B6",
    },
    {
        "char_id": "yuri_school",
        "doki_id": "yuri",
        "rel_path": "yuri escuela/Yuri_school_1.webp",
        "center_x_px": 505.0,
        "color_hex": "#A855F7",
    },
    {
        "char_id": "yuri_casual",
        "doki_id": "yuri",
        "rel_path": "yuri casual/Casualclothes1-0.webp",
        "center_x_px": 505.0,
        "color_hex": "#A855F7",
    },
]


def smoothstep(edge0, edge1, x):
    t = np.clip((x - edge0) / max(1e-6, (edge1 - edge0)), 0.0, 1.0)
    return float(t * t * (3.0 - 2.0 * t))


def compute_hd_bone_weights(x, z_norm, char_id):
    """
    Full-scale Non-Chibi Anime proportions for z_norm in [0.0 .. 1.0]:
    - Head & Face: z_norm >= 0.63 (Z >= 1.23) -> 100% rigid on `head` so eyes/face never distort
    - Upper Torso & Chest: z_norm in [0.33 .. 0.63] -> `spine` + lateral `arm_l`/`arm_r` + `hair_l`/`hair_r`
    - Waist & Hips: z_norm in [0.18 .. 0.33] -> `hips` + low forearms/hands
    - Skirt / Shorts / Thighs: z_norm < 0.18 -> `leg_l` / `leg_r`
    """
    w = {
        "root": 0.0,
        "hips": 0.0,
        "spine": 0.0,
        "head": 0.0,
        "hair_l": 0.0,
        "hair_r": 0.0,
        "arm_l": 0.0,
        "arm_r": 0.0,
        "leg_l": 0.0,
        "leg_r": 0.0,
    }

    is_head = smoothstep(0.60, 0.66, z_norm)
    is_upper = smoothstep(0.30, 0.37, z_norm) * (1.0 - is_head)
    is_lower = 1.0 - is_head - is_upper

    is_yuri = char_id.startswith("yuri")
    is_monika = char_id.startswith("monika")

    if is_head > 0.001:
        # Keep head & hair above the shoulders unified on `head` so no seam ever tears between face and hair
        w["head"] += is_head

    if is_upper > 0.001:
        if is_yuri or is_monika:
            # Monika's ponytail and Yuri's back hair touch the torso continuously;
            # blend gently so the silhouette never tears at x=0.22
            arm_lat = smoothstep(0.20, 0.36, abs(x))
            arm_w = 0.22 * arm_lat
            w["spine"] += is_upper * (1.0 - arm_w)
            if x < 0:
                w["arm_l"] += is_upper * arm_w
            else:
                w["arm_r"] += is_upper * arm_w
        else:
            arm_lat = smoothstep(0.18, 0.34, abs(x))
            arm_w = 0.32 * arm_lat
            w["spine"] += is_upper * (1.0 - arm_w)
            if x < 0:
                w["arm_l"] += is_upper * arm_w
            else:
                w["arm_r"] += is_upper * arm_w

    if is_lower > 0.001:
        hand_lat = 0.0
        if not is_yuri:
            hand_lat = 0.28 * smoothstep(0.22, 0.35, abs(x)) * smoothstep(0.03, 0.14, z_norm)
            if hand_lat > 0.001:
                if x < 0:
                    w["arm_l"] += is_lower * hand_lat
                else:
                    w["arm_r"] += is_lower * hand_lat

        body_lower = is_lower * (1.0 - hand_lat)
        leg_zone = 1.0 - smoothstep(0.14, 0.25, z_norm)
        hips_zone = 1.0 - leg_zone
        w["hips"] += body_lower * hips_zone
        if leg_zone > 0.001:
            right_ratio = smoothstep(-0.10, 0.10, x)
            w["leg_l"] += body_lower * leg_zone * (1.0 - right_ratio)
            w["leg_r"] += body_lower * leg_zone * right_ratio

    total = sum(w.values())
    if total > 1e-6:
        for k in w:
            w[k] /= total
    else:
        w["hips"] = 1.0
    return w


def build_hd_character_3d_mesh_json(char_cfg):
    char_id = char_cfg["char_id"]
    src_path = os.path.join(
        ROOT, "android_app", "app", "src", "main", "assets", "ddsc",
        char_cfg["rel_path"]
    )
    im = Image.open(src_path).convert("RGBA")
    arr = np.array(im)

    alpha = arr[:, :, 3]
    ys, xs = np.where(alpha > 20)
    y0, y1 = max(0, int(ys.min()) - 4), min(arr.shape[0], int(ys.max()) + 1)
    x0, x1 = max(0, int(xs.min()) - 4), min(arr.shape[1], int(xs.max()) + 4)
    crop = arr[y0:y1, x0:x1].copy()
    ch, cw, _ = crop.shape

    # True anatomical centerline normalized to [0..1] in cropped image
    center_u = (char_cfg["center_x_px"] - x0) / float(max(1, cw - 1))

    # Gently round the outer bottom corners of the skirt/shorts hem so it has a natural 3D contour
    # instead of a sharp horizontal frame border cut
    for ry in range(max(0, ch - 18), ch):
        row_a = crop[ry, :, 3]
        opaque_xs = np.where(row_a > 24)[0]
        if len(opaque_xs) < 10:
            continue
        lx, rx = int(opaque_xs.min()), int(opaque_xs.max())
        span = max(1.0, float(rx - lx))
        v_bot = (ry - (ch - 18)) / 18.0  # 0 at ch-18, 1 at bottom
        for cx_i in range(lx, rx + 1):
            rel_x = abs((cx_i - (lx + rx) * 0.5) / (span * 0.5))
            # Curve outer edges upward slightly
            edge_curve = (rel_x ** 2.4) * 0.85
            if v_bot > (1.0 - edge_curve):
                fade = max(0.0, 1.0 - (v_bot - (1.0 - edge_curve)) * 5.0)
                crop[ry, cx_i, 3] = int(crop[ry, cx_i, 3] * fade)

    # Dilate RGB into transparent pixels so bilinear texture sampling has zero dark halos
    c_alpha = crop[:, :, 3]
    mask_hi = c_alpha > 42
    _, nearest_idx = distance_transform_edt(~mask_hi, return_indices=True)
    dilated_rgb = crop[:, :, :3].copy()
    for ch_idx in range(3):
        channel = dilated_rgb[:, :, ch_idx]
        dilated_rgb[:, :, ch_idx] = channel[tuple(nearest_idx)]
    tex_rgba = np.dstack([dilated_rgb, np.full_like(c_alpha, 255)])

    os.makedirs(SCRATCH_DIR, exist_ok=True)
    tex_out_path = os.path.join(SCRATCH_DIR, f"{char_id}_tex.png")
    Image.fromarray(tex_rgba, "RGBA").save(tex_out_path)

    grid_w, grid_h = 90, 124
    mask_grid_im = Image.fromarray((mask_hi.astype(np.uint8) * 255), "L").resize(
        (grid_w, grid_h), Image.Resampling.BILINEAR
    )
    grid_mask = np.array(mask_grid_im) > 152

    # Smooth distance field for organic 3D anime relief
    edt = distance_transform_edt(grid_mask)
    edt_smooth = gaussian_filter(edt.astype(np.float32), sigma=4.4)
    max_edt = max(1.0, float(edt_smooth.max()))
    norm_edt = np.clip(edt_smooth / max_edt, 0.0, 1.0)

    char_h_world = 1.88
    aspect = cw / float(ch)
    char_w_world = char_h_world * aspect

    verts = []
    uvs = []
    weights_map = {
        "root": [], "hips": [], "spine": [], "head": [],
        "hair_l": [], "hair_r": [], "arm_l": [], "arm_r": [],
        "leg_l": [], "leg_r": [],
    }

    front_idx = np.full((grid_h, grid_w), -1, dtype=np.int32)
    back_idx = np.full((grid_h, grid_w), -1, dtype=np.int32)
    boundary_vert_indices = set()

    for r in range(grid_h):
        for c in range(grid_w):
            if not grid_mask[r, c]:
                continue

            u = c / float(grid_w - 1)
            v_img = r / float(grid_h - 1)
            z_norm = 1.0 - v_img
            uv = [float(u), float(z_norm)]

            # Center X around the character's true anatomical spine centerline!
            x = (u - center_u) * char_w_world
            z = 0.05 + z_norm * char_h_world

            is_boundary = False
            for dr, dc in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
                nr, nc = r + dr, c + dc
                if nr < 0 or nr >= grid_h or nc < 0 or nc >= grid_w or not grid_mask[nr, nc]:
                    is_boundary = True
                    break

            if is_boundary:
                depth = 0.0
            else:
                base_dome = math.sin(norm_edt[r, c] * (math.pi * 0.5)) * 0.145
                bust_bump = math.exp(-((z_norm - 0.46) / 0.12) ** 2) * math.exp(-((x) / 0.18) ** 2) * 0.020
                depth = base_dome + bust_bump * norm_edt[r, c]

            bw = compute_hd_bone_weights(x, z_norm, char_id)

            f_i = len(verts)
            verts.append([float(x), float(-depth), float(z)])
            uvs.append(uv)
            for bname, wval in bw.items():
                if wval > 0.001:
                    weights_map[bname].append([f_i, round(float(wval), 4)])
            front_idx[r, c] = f_i

            if is_boundary or depth <= 1e-4:
                back_idx[r, c] = f_i
                boundary_vert_indices.add(f_i)
            else:
                b_i = len(verts)
                verts.append([float(x), float(depth * 0.82), float(z)])
                uvs.append(uv)
                for bname, wval in bw.items():
                    if wval > 0.001:
                        weights_map[bname].append([b_i, round(float(wval), 4)])
                back_idx[r, c] = b_i

    faces = []
    adj = {vi: set() for vi in boundary_vert_indices}

    def register_edge(u_i, v_i):
        if u_i in boundary_vert_indices and v_i in boundary_vert_indices and u_i != v_i:
            adj[u_i].add(v_i)
            adj[v_i].add(u_i)

    for r in range(grid_h - 1):
        for c in range(grid_w - 1):
            if (
                grid_mask[r, c]
                and grid_mask[r + 1, c]
                and grid_mask[r, c + 1]
                and grid_mask[r + 1, c + 1]
            ):
                f00 = int(front_idx[r, c])
                f10 = int(front_idx[r + 1, c])
                f11 = int(front_idx[r + 1, c + 1])
                f01 = int(front_idx[r, c + 1])
                if len({f00, f10, f11}) == 3:
                    faces.append([f00, f10, f11])
                    register_edge(f00, f10)
                    register_edge(f10, f11)
                    register_edge(f11, f00)
                if len({f00, f11, f01}) == 3:
                    faces.append([f00, f11, f01])
                    register_edge(f00, f11)
                    register_edge(f11, f01)
                    register_edge(f01, f00)

                b00 = int(back_idx[r, c])
                b10 = int(back_idx[r + 1, c])
                b11 = int(back_idx[r + 1, c + 1])
                b01 = int(back_idx[r, c + 1])
                if len({b00, b11, b10}) == 3:
                    faces.append([b00, b11, b10])
                if len({b00, b01, b11}) == 3:
                    faces.append([b00, b01, b11])

    # Smooth boundary contour in X/Z plane to eliminate grid stair-stepping
    for _ in range(4):
        new_pos = {}
        for vi in boundary_vert_indices:
            nbrs = adj[vi]
            if len(nbrs) >= 2:
                avg_x = sum(verts[n][0] for n in nbrs) / len(nbrs)
                avg_z = sum(verts[n][2] for n in nbrs) / len(nbrs)
                new_pos[vi] = (
                    0.45 * verts[vi][0] + 0.55 * avg_x,
                    0.45 * verts[vi][2] + 0.55 * avg_z,
                )
        for vi, (nx, nz) in new_pos.items():
            verts[vi][0] = round(nx, 5)
            verts[vi][2] = round(nz, 5)

    for v in verts:
        v[0] = round(v[0], 5)
        v[1] = round(v[1], 5)
        v[2] = round(v[2], 5)

    mesh_json_path = os.path.join(SCRATCH_DIR, f"{char_id}_mesh.json")
    data = {
        "char_id": char_id,
        "doki_id": char_cfg["doki_id"],
        "tex_path": tex_out_path,
        "color_hex": char_cfg["color_hex"],
        "verts": verts,
        "faces": faces,
        "uvs": uvs,
        "weights": weights_map,
    }
    with open(mesh_json_path, "w", encoding="utf-8") as f:
        json.dump(data, f)

    print(f"[HD MeshGen] {char_id}: {len(verts)} verts, {len(faces)} tris -> {mesh_json_path}")
    return mesh_json_path


def create_hd_showcase_gifs_and_posters():
    showcase_dir = os.path.join(ROOT, "docs", "characters", "3d_hd")
    os.makedirs(showcase_dir, exist_ok=True)

    for cfg in HD_CHARACTERS:
        cid = cfg["char_id"]
        render_dir = os.path.join(
            ROOT, "android_app", "app", "src", "main", "assets", "doki3d_hd", cid
        )
        dance_frames = []
        for i in range(12):
            fp = os.path.join(render_dir, f"dance_{i:02d}.webp")
            if os.path.exists(fp):
                im = Image.open(fp).convert("RGBA")
                dance_frames.append(im)

        if dance_frames:
            poster_frame = dance_frames[2] if len(dance_frames) > 2 else dance_frames[0]
            poster_path = os.path.join(showcase_dir, f"{cid}_3d.png")
            poster_frame.save(poster_path, "PNG")

            gif_path = os.path.join(showcase_dir, f"{cid}_3d_dance.gif")
            gif_frames = []
            for fr in dance_frames:
                rgb = Image.new("RGBA", fr.size, (18, 18, 26, 255))
                rgb.alpha_composite(fr)
                p_img = rgb.convert("RGB").quantize(colors=255, method=Image.Quantize.MEDIANCUT)
                gif_frames.append(p_img)
            gif_frames[0].save(
                gif_path,
                save_all=True,
                append_images=gif_frames[1:],
                duration=75,
                loop=0,
            )
            print(f"[HD Showcase] Saved {poster_path} & {gif_path}")


def main():
    os.makedirs(SCRATCH_DIR, exist_ok=True)
    manifest_entries = []
    for cfg in HD_CHARACTERS:
        cid = cfg["char_id"]
        mesh_json = build_hd_character_3d_mesh_json(cfg)
        render_out_dir = os.path.join(
            ROOT, "android_app", "app", "src", "main", "assets", "doki3d_hd", cid
        )
        glb_out_path = os.path.join(ROOT, "docs", "models3d", f"{cid}_3d.glb")
        manifest_entries.append({
            "char_id": cid,
            "mesh_json": mesh_json,
            "render_out_dir": render_out_dir,
            "glb_out_path": glb_out_path,
        })

    manifest_path = os.path.join(SCRATCH_DIR, "blender_hd_manifest.json")
    with open(manifest_path, "w", encoding="utf-8") as f:
        json.dump({"characters": manifest_entries}, f, indent=2)

    print("[Blender 5.2 HD] Launching headless Blender 5.2 Workbench 3D HD pipeline...")
    cmd = [
        BLENDER_EXE,
        "--background",
        "--python",
        BLENDER_SCRIPT,
        "--",
        manifest_path,
    ]
    res = subprocess.run(cmd, check=True)
    print(f"[Blender 5.2 HD] Completed with return code {res.returncode}")

    create_hd_showcase_gifs_and_posters()


if __name__ == "__main__":
    main()
