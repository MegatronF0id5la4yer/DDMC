import os
import json
import math
import subprocess
from collections import deque
import numpy as np
from PIL import Image
from scipy.ndimage import distance_transform_edt, gaussian_filter

ROOT = r"d:\music PP\sonora"
BLENDER_EXE = r"C:\Program Files\Blender Foundation\Blender 5.2\blender.exe"
BLENDER_SCRIPT = os.path.join(ROOT, "scripts", "blender_rig_and_render.py")
SCRATCH_DIR = os.path.join(ROOT, "build_3d_tmp")

CHARACTERS = [
    {
        "char_id": "monika",
        "skin_folder": "Monika",
        "sprite": "stand1.png",
        "color_hex": "#50C878",
    },
    {
        "char_id": "sayori",
        "skin_folder": "Sayori",
        "sprite": "stand1.png",
        "color_hex": "#FF9966",
    },
    {
        "char_id": "natsuki",
        "skin_folder": "Natsuki",
        "sprite": "stand1.png",
        "color_hex": "#FF66B2",
    },
    {
        "char_id": "yuri",
        "skin_folder": "Yuri",
        "sprite": "stand1.png",
        "color_hex": "#9966CC",
    },
    {
        "char_id": "bocchi",
        "skin_folder": "Bocchi",
        "sprite": "stand1.png",
        "color_hex": "#F06292",
    },
    {
        "char_id": "konata",
        "skin_folder": "Konata",
        "sprite": "stand1.png",
        "color_hex": "#42A5F5",
    },
]


def smoothstep(edge0, edge1, x):
    t = np.clip((x - edge0) / max(1e-6, (edge1 - edge0)), 0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)


def remove_enclosed_sticker_pockets(arr: np.ndarray) -> np.ndarray:
    """
    Removes enclosed paper-sticker holes (such as the gap between Monika's cheek/shoulder
    and her ponytail) where a pure-white pocket is ringed by a neutral grey sticker shadow.
    """
    out = arr.copy()
    h, w, _ = out.shape
    r = out[:, :, 0].astype(np.int32)
    g = out[:, :, 1].astype(np.int32)
    b = out[:, :, 2].astype(np.int32)
    a = out[:, :, 3].astype(np.int32)
    sat = np.maximum(np.maximum(r, g), b) - np.minimum(np.minimum(r, g), b)
    min_c = np.minimum(np.minimum(r, g), b)

    is_sticker_pocket = (a > 0) & (sat <= 14) & (min_c >= 145)
    visited = np.zeros((h, w), dtype=bool)
    q = deque()

    for y in range(int(h * 0.67), int(h * 0.79)):
        for x in range(int(w * 0.54), int(w * 0.67)):
            if (a[y, x] > 200) and (sat[y, x] <= 6) and (min_c[y, x] >= 244):
                visited[y, x] = True
                q.append((y, x))

    while q:
        cy, cx = q.popleft()
        for ny, nx in ((cy - 1, cx), (cy + 1, cx), (cy, cx - 1), (cy, cx + 1)):
            if int(h * 0.63) <= ny < int(h * 0.83) and int(w * 0.50) <= nx < int(w * 0.72) and not visited[ny, nx]:
                if is_sticker_pocket[ny, nx]:
                    visited[ny, nx] = True
                    q.append((ny, nx))

    out[visited, 3] = 0
    return out


def compute_bone_weights(x, z):
    """
    Chibi / Nendoroid anatomical bone weights for (x, z) in Z=[0.04..1.94]:
    - Head occupies Z >= 0.76 (keeps face 100% rigid & distortion-free!)
    - Spine & Arms occupy Z in [0.40 .. 0.76]
    - Hips & Legs occupy Z < 0.40
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

    is_head = float(smoothstep(0.72, 0.84, z))
    is_upper = float(smoothstep(0.38, 0.48, z)) * (1.0 - is_head)
    is_lower = 1.0 - is_head - is_upper

    if is_head > 0.001:
        # Keep the face and cranium rigid on `head`; only outer side-hair tips sway gently
        hair_lat = float(smoothstep(0.32, 0.48, abs(x))) * float(1.0 - smoothstep(1.35, 1.65, z))
        hair_w = 0.30 * hair_lat
        w["head"] += is_head * (1.0 - hair_w)
        if x < 0:
            w["hair_l"] += is_head * hair_w
        else:
            w["hair_r"] += is_head * hair_w

    if is_upper > 0.001:
        arm_lat = float(smoothstep(0.16, 0.30, abs(x)))
        arm_w = 0.55 * arm_lat
        w["spine"] += is_upper * (1.0 - arm_w)
        if x < 0:
            w["arm_l"] += is_upper * arm_w
        else:
            w["arm_r"] += is_upper * arm_w

    if is_lower > 0.001:
        leg_zone = float(1.0 - smoothstep(0.28, 0.40, z))
        hips_zone = 1.0 - leg_zone
        w["hips"] += is_lower * hips_zone
        if leg_zone > 0.001:
            right_ratio = float(smoothstep(-0.04, 0.04, x))
            w["leg_l"] += is_lower * leg_zone * (1.0 - right_ratio)
            w["leg_r"] += is_lower * leg_zone * right_ratio

    total = sum(w.values())
    if total > 1e-6:
        for k in w:
            w[k] /= total
    else:
        w["hips"] = 1.0
    return w


def build_character_3d_mesh_json(char_cfg):
    char_id = char_cfg["char_id"]
    src_path = os.path.join(
        ROOT, "android_app", "app", "src", "main", "assets", "skins",
        char_cfg["skin_folder"], char_cfg["sprite"]
    )
    im = Image.open(src_path).convert("RGBA")
    arr = np.array(im)
    if char_id == "monika":
        arr = remove_enclosed_sticker_pockets(arr)
        Image.fromarray(arr, "RGBA").save(src_path)

    alpha = arr[:, :, 3]
    ys, xs = np.where(alpha > 20)
    y0, y1 = max(0, int(ys.min()) - 3), min(arr.shape[0], int(ys.max()) + 4)
    x0, x1 = max(0, int(xs.min()) - 3), min(arr.shape[1], int(xs.max()) + 4)
    crop = arr[y0:y1, x0:x1].copy()

    # Keep crisp original pixel art upscaled with NEAREST (2x) + slight LANCZOS (2x) so eyes & lines stay sharp!
    crop_im = Image.fromarray(crop, "RGBA")
    up1 = crop_im.resize((crop_im.width * 2, crop_im.height * 2), Image.Resampling.NEAREST)
    up_im = up1.resize((crop_im.width * 4, crop_im.height * 4), Image.Resampling.BILINEAR)
    up_arr = np.array(up_im)

    c_alpha = up_arr[:, :, 3]
    mask_hi = c_alpha > 48
    _, nearest_idx = distance_transform_edt(~mask_hi, return_indices=True)
    dilated_rgb = up_arr[:, :, :3].copy()
    for ch in range(3):
        channel = dilated_rgb[:, :, ch]
        dilated_rgb[:, :, ch] = channel[tuple(nearest_idx)]
    tex_rgba = np.dstack([dilated_rgb, np.full_like(c_alpha, 255)])

    os.makedirs(SCRATCH_DIR, exist_ok=True)
    tex_out_path = os.path.join(SCRATCH_DIR, f"{char_id}_tex.png")
    Image.fromarray(tex_rgba, "RGBA").save(tex_out_path)

    grid_w, grid_h = 84, 104
    mask_grid_im = Image.fromarray((mask_hi.astype(np.uint8) * 255), "L").resize(
        (grid_w, grid_h), Image.Resampling.BILINEAR
    )
    grid_mask = np.array(mask_grid_im) > 110

    # Smooth distance field with larger sigma so the 3D surface is a gentle, ridge-free dome
    edt = distance_transform_edt(grid_mask)
    edt_smooth = gaussian_filter(edt.astype(np.float32), sigma=3.8)
    max_edt = max(1.0, float(edt_smooth.max()))
    norm_edt = np.clip(edt_smooth / max_edt, 0.0, 1.0)

    char_h_world = 1.88
    aspect = crop.shape[1] / float(crop.shape[0])
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
            uv = [float(u), float(1.0 - v_img)]

            x = (u - 0.5) * char_w_world
            z = 0.05 + (1.0 - v_img) * char_h_world

            is_boundary = False
            for dr, dc in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
                nr, nc = r + dr, c + dc
                if nr < 0 or nr >= grid_h or nc < 0 or nc >= grid_w or not grid_mask[nr, nc]:
                    is_boundary = True
                    break

            if is_boundary:
                depth = 0.0
            else:
                # Smooth, ridge-free sinusoidal 3D relief dome (no abrupt cliff at boundary!)
                depth = math.sin(norm_edt[r, c] * (math.pi * 0.5)) * 0.16

            bw = compute_bone_weights(x, z)

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
                verts.append([float(x), float(depth * 0.85), float(z)])
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
        "tex_path": tex_out_path,
        "color_hex": char_cfg["color_hex"],
        "verts": verts,
        "faces": faces,
        "uvs": uvs,
        "weights": weights_map,
    }
    with open(mesh_json_path, "w", encoding="utf-8") as f:
        json.dump(data, f)

    print(f"[MeshGen] {char_id}: {len(verts)} verts, {len(faces)} tris -> {mesh_json_path}")
    return mesh_json_path


def create_showcase_gifs_and_posters():
    showcase_dir = os.path.join(ROOT, "docs", "characters", "3d")
    os.makedirs(showcase_dir, exist_ok=True)

    for cfg in CHARACTERS:
        cid = cfg["char_id"]
        render_dir = os.path.join(
            ROOT, "android_app", "app", "src", "main", "assets", "doki3d", cid
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
            print(f"[Showcase] Saved {poster_path} & {gif_path}")


def main():
    os.makedirs(SCRATCH_DIR, exist_ok=True)
    manifest_entries = []
    for cfg in CHARACTERS:
        cid = cfg["char_id"]
        mesh_json = build_character_3d_mesh_json(cfg)
        render_out_dir = os.path.join(
            ROOT, "android_app", "app", "src", "main", "assets", "doki3d", cid
        )
        glb_out_path = os.path.join(ROOT, "docs", "models3d", f"{cid}_3d.glb")
        manifest_entries.append({
            "char_id": cid,
            "mesh_json": mesh_json,
            "render_out_dir": render_out_dir,
            "glb_out_path": glb_out_path,
        })

    manifest_path = os.path.join(SCRATCH_DIR, "blender_manifest.json")
    with open(manifest_path, "w", encoding="utf-8") as f:
        json.dump({"characters": manifest_entries}, f, indent=2)

    print(f"[Blender 5.2] Launching headless Blender 5.2 Workbench 3D pipeline...")
    cmd = [
        BLENDER_EXE,
        "--background",
        "--python",
        BLENDER_SCRIPT,
        "--",
        manifest_path,
    ]
    res = subprocess.run(cmd, check=True)
    print(f"[Blender 5.2] Completed with return code {res.returncode}")

    create_showcase_gifs_and_posters()


if __name__ == "__main__":
    main()
