import os
import glob
from collections import deque
import numpy as np
from PIL import Image, ImageDraw, ImageFilter
from scipy.ndimage import label, binary_dilation

ROOT = str(Path(__file__).resolve().parents[1])
ASSETS_SKINS = os.path.join(ROOT, "android_app", "app", "src", "main", "assets", "skins")
ASSETS_DDSC = os.path.join(ROOT, "android_app", "app", "src", "main", "assets", "ddsc")
RES_DRAWABLE = os.path.join(ROOT, "android_app", "app", "src", "main", "res", "drawable")
DOCS_CHARS = os.path.join(ROOT, "docs", "characters")
DOCS_SCREENSHOTS = os.path.join(ROOT, "docs", "screenshots")


def clean_chibi_sticker(im: Image.Image) -> Image.Image:
    """
    Removes the baked-in white paper sticker border and grey outer shadow ring
    from PinkChan / Shimeji sprites while preserving 100% of interior whites
    (ribbons, eyes, collars, socks) and keeping only the main connected character(s).
    """
    im = im.convert("RGBA")
    arr = np.array(im, dtype=np.int32)
    h, w, _ = arr.shape
    r, g, b, a = arr[:, :, 0], arr[:, :, 1], arr[:, :, 2], arr[:, :, 3]

    max_rgb = np.maximum(np.maximum(r, g), b)
    min_rgb = np.minimum(np.minimum(r, g), b)
    sat = max_rgb - min_rgb

    # Exterior paper sticker border is transparent, white, or neutral light/mid-grey (low saturation)
    is_border_cand = (
        (a <= 15)
        | ((sat <= 18) & (min_rgb >= 135))
        | ((a < 235) & (sat <= 35) & (min_rgb >= 115))
    )

    visited = np.zeros((h, w), dtype=bool)
    q = deque()
    for y in range(h):
        for x in (0, w - 1):
            if is_border_cand[y, x] and not visited[y, x]:
                visited[y, x] = True
                q.append((y, x))
    for x in range(w):
        for y in (0, h - 1):
            if is_border_cand[y, x] and not visited[y, x]:
                visited[y, x] = True
                q.append((y, x))

    while q:
        cy, cx = q.popleft()
        for ny, nx in ((cy - 1, cx), (cy + 1, cx), (cy, cx - 1), (cy, cx + 1)):
            if 0 <= ny < h and 0 <= nx < w and not visited[ny, nx]:
                if is_border_cand[ny, nx]:
                    visited[ny, nx] = True
                    q.append((ny, nx))

    out = arr.copy()
    out[visited, 3] = 0

    # Soften 1px anti-aliased fringe where sticker border touched character outline
    fringe = binary_dilation(visited, iterations=1) & (~visited)
    light_fringe = fringe & (sat <= 26) & (min_rgb >= 165)
    out[light_fringe, 3] = (out[light_fringe, 3] * 0.20).astype(np.int32)

    # Remove tiny disconnected shadow specks (< 25 pixels) left outside the character
    opaque_mask = out[:, :, 3] > 20
    labeled, num_features = label(opaque_mask)
    if num_features > 1:
        counts = np.bincount(labeled.ravel())
        counts[0] = 0
        max_area = counts.max() if len(counts) > 1 else 0
        for comp_id in range(1, num_features + 1):
            if counts[comp_id] < max(28, int(max_area * 0.015)):
                out[labeled == comp_id, 3] = 0

    return Image.fromarray(out.astype(np.uint8), "RGBA")


def clean_bocchi_halo(im: Image.Image) -> Image.Image:
    """Removes white/pinkish semi-transparent halo fringing around Bocchi frames."""
    im = im.convert("RGBA")
    arr = np.array(im, dtype=np.int32)
    r, g, b, a = arr[:, :, 0], arr[:, :, 1], arr[:, :, 2], arr[:, :, 3]
    # Remove faint alpha noise
    arr[a < 35, 3] = 0
    # De-contaminate white matte on semi-transparent edge pixels
    semi = (a >= 35) & (a < 230)
    alpha_f = a[semi] / 255.0
    for ch in range(3):
        c = arr[:, :, ch][semi]
        # Un-premultiply against white (255)
        unmatted = np.clip((c - 255.0 * (1.0 - alpha_f)) / np.maximum(alpha_f, 0.15), 0, 255)
        arr[:, :, ch][semi] = unmatted.astype(np.int32)
    return Image.fromarray(arr.astype(np.uint8), "RGBA")


def build_konata_guitar_frames(konata_dir: str):
    """
    Builds clean guitar1.png, guitar2.png, guitar3.png for Konata from kona_1.png
    so her face and eyes are 100% visible while holding a stylized electric guitar over her torso.
    """
    base_path = os.path.join(konata_dir, "kona_1.png")
    if not os.path.exists(base_path):
        base_path = os.path.join(konata_dir, "stand1.png")
    base = Image.open(base_path).convert("RGBA")

    for idx in range(1, 4):
        frame = base.copy()
        draw = ImageDraw.Draw(frame)
        # Konata stands around x=38..91, y=30..124; her torso is y=74..102, chin is at y=70
        # Draw a crisp pixel-art cherry-burst electric guitar across her waist/chest (y=75..102)
        dy = (idx % 2) * 2 - 1
        # Guitar strap over shoulder
        draw.line([(54, 71), (46, 88)], fill=(40, 40, 48, 255), width=2)
        draw.line([(74, 72), (78, 86)], fill=(40, 40, 48, 255), width=2)
        # Guitar neck (angled to the right, well below her eyes!)
        neck_pts = [(64, 86 + dy), (96, 68 + dy), (99, 72 + dy), (67, 90 + dy)]
        draw.polygon(neck_pts, fill=(235, 200, 145, 255), outline=(25, 20, 20, 255))
        # Guitar headstock
        head_pts = [(95, 66 + dy), (105, 61 + dy), (107, 67 + dy), (98, 72 + dy)]
        draw.polygon(head_pts, fill=(210, 35, 65, 255), outline=(20, 20, 20, 255))
        # Guitar body (double-cutaway rock body on torso y=78..103)
        bx, by = 58, 90 + dy
        draw.ellipse([(bx - 16, by - 11), (bx + 15, by + 11)], fill=(225, 38, 70, 255), outline=(20, 18, 22, 255), width=2)
        # White pickguard + pickups
        draw.ellipse([(bx - 9, by - 6), (bx + 8, by + 7)], fill=(248, 248, 252, 255), outline=(40, 40, 50, 255), width=1)
        # Strings & bridge
        draw.line([(bx - 6, by + 1), (95, 70 + dy)], fill=(220, 230, 245, 255), width=1)
        # Strumming hand animation
        hx = bx + (-2 if idx == 1 else (1 if idx == 2 else 3))
        hy = by + (-2 if idx == 1 else (2 if idx == 2 else 0))
        draw.ellipse([(hx - 4, hy - 4), (hx + 4, hy + 4)], fill=(255, 224, 196, 255), outline=(25, 20, 20, 255), width=1)

        frame.save(os.path.join(konata_dir, f"guitar{idx}.png"))


def ensure_complete_skin_frames(skin_dir: str, is_bocchi: bool = False, is_konata: bool = False):
    """
    Ensures every frame name requested by DdscEngine.selectChibiFrameName exists
    so characters never vanish during jumps, climbs, drags, or ceiling walks.
    """
    def copy_if_missing(target_name: str, candidates: list[str], flip_v: bool = False, tilt_deg: float = 0.0):
        target_path = os.path.join(skin_dir, target_name)
        if os.path.exists(target_path):
            return
        for c in candidates:
            src_p = os.path.join(skin_dir, c)
            if os.path.exists(src_p):
                im = Image.open(src_p).convert("RGBA")
                if flip_v:
                    im = im.transpose(Image.Transpose.FLIP_TOP_BOTTOM)
                if abs(tilt_deg) > 0.1:
                    im = im.rotate(tilt_deg, resample=Image.Resampling.NEAREST, expand=False)
                im.save(target_path)
                return

    copy_if_missing("stand.png", ["stand1.png", "kona_1.png", "walk1.png"])
    copy_if_missing("stand1.png", ["stand.png", "kona_1.png", "walk1.png"])
    copy_if_missing("stand2.png", ["stand1.png", "walk2.png", "kona_2.png"])
    copy_if_missing("stand3.png", ["stand2.png", "walk3.png", "kona_3.png"])
    copy_if_missing("jump1.png", ["jump_2.png", "jump_1.png", "kona_20.png", "fall1.png", "walk2.png"])
    copy_if_missing("land1.png", ["jump_1.png", "sit1.png", "kona_15.png", "stand1.png"])
    copy_if_missing("jump_1.png", ["jump1.png", "walk1.png"])
    copy_if_missing("jump_2.png", ["jump1.png", "fall1.png"])
    copy_if_missing("jump_3.png", ["land1.png", "stand1.png"])
    copy_if_missing("snug.png", ["sit1.png", "kona_10.png", "stand1.png"])
    copy_if_missing("climb.png", ["climb1.png", "walk1.png"])
    copy_if_missing("climb1.png", ["climb.png", "walk1.png", "back1.png"])
    copy_if_missing("climb2.png", ["climb1.png", "walk2.png", "back2.png"])
    copy_if_missing("climb_top.png", ["ceiling1.png", "climb1.png", "walk1.png"])
    copy_if_missing("ceiling1.png", ["climb_top.png", "kona_11.png", "climb1.png", "walk1.png"])
    copy_if_missing("ceiling2.png", ["climb_top.png", "kona_12.png", "climb2.png", "walk2.png"])
    copy_if_missing("air.png", ["fall1.png", "jump1.png", "stand1.png"])
    copy_if_missing("fall1.png", ["air.png", "jump_2.png", "stand1.png"])
    copy_if_missing("air_swing_l.png", ["fall1.png", "air.png", "stand1.png"], tilt_deg=-12.0)
    copy_if_missing("air_swing_r.png", ["fall1.png", "air.png", "stand1.png"], tilt_deg=12.0)
    copy_if_missing("clone_1.png", ["stand1.png", "stand.png"])
    copy_if_missing("clone_2.png", ["walk1.png", "stand2.png"])
    copy_if_missing("clone_3.png", ["sit1.png", "snug.png"])
    copy_if_missing("clone_4.png", ["jump1.png", "jump_2.png"])


def extract_hd_cutout(webp_rel: str, canvas_size=(512, 680), fade_bottom: bool = True) -> Image.Image:
    """
    Extracts a full, unclipped high-resolution character cutout from the 960x960 DDSC .webp master,
    fitting the ENTIRE character (hair, ribbon, arms, skirt) inside `canvas_size` with a soft
    bottom feather so thigh-cut edges never look abruptly severed.
    """
    p = os.path.join(ASSETS_DDSC, webp_rel)
    im = Image.open(p).convert("RGBA")
    arr = np.array(im, dtype=np.int32)
    # Tight opaque bounding box (alpha > 12)
    ys, xs = np.where(arr[:, :, 3] > 12)
    if len(xs) == 0:
        return im.resize(canvas_size, Image.Resampling.LANCZOS)

    x0, x1 = max(0, int(xs.min()) - 4), min(im.width, int(xs.max()) + 5)
    y0, y1 = max(0, int(ys.min()) - 4), min(im.height, int(ys.max()) + 5)
    touches_bottom = y1 >= im.height - 4

    cropped = im.crop((x0, y0, x1, y1))
    cw, ch = cropped.size

    if fade_bottom and touches_bottom:
        c_arr = np.array(cropped, dtype=np.float32)
        fade_h = max(16, int(ch * 0.065))
        for fy in range(ch - fade_h, ch):
            t = (ch - 1 - fy) / float(fade_h)
            # Smooth cosine ease
            factor = 0.5 - 0.5 * np.cos(np.pi * t)
            c_arr[fy, :, 3] *= factor
        cropped = Image.fromarray(c_arr.astype(np.uint8), "RGBA")

    tw, th = canvas_size
    pad_top = int(th * 0.025)
    avail_w = int(tw * 0.94)
    avail_h = th - pad_top
    scale = min(avail_w / float(cw), avail_h / float(ch))
    nw = max(1, int(round(cw * scale)))
    nh = max(1, int(round(ch * scale)))

    resized = cropped.resize((nw, nh), Image.Resampling.LANCZOS)
    out = Image.new("RGBA", (tw, th), (0, 0, 0, 0))
    ox = (tw - nw) // 2
    oy = th - nh
    out.paste(resized, (ox, oy), resized)
    return out


def make_clean_chibi_showcase(img_path: str, size=(256, 256)) -> Image.Image:
    im = Image.open(img_path).convert("RGBA")
    bbox = im.getbbox()
    if bbox:
        im = im.crop(bbox)
    cw, ch = im.size
    tw, th = size
    scale = min((tw * 0.86) / cw, (th * 0.88) / ch)
    nw = max(1, int(round(cw * scale)))
    nh = max(1, int(round(ch * scale)))
    # Use NEAREST for pixel-art Konata, LANCZOS for higher-res Chibis
    resample = Image.Resampling.NEAREST if "Konata" in img_path else Image.Resampling.LANCZOS
    resized = im.resize((nw, nh), resample)
    out = Image.new("RGBA", (tw, th), (0, 0, 0, 0))
    ox = (tw - nw) // 2
    oy = (th - nh) // 2
    out.paste(resized, (ox, oy), resized)
    return out


def main():
    # 1. Clean sticker borders on Monika, Sayori, Natsuki, Yuri in assets/skins and assets/ddsc
    for char_name in ["Monika", "Sayori", "Natsuki", "Yuri"]:
        for root_dir in [ASSETS_SKINS, ASSETS_DDSC]:
            folder = os.path.join(root_dir, char_name)
            if not os.path.isdir(folder):
                continue
            for png_path in glob.glob(os.path.join(folder, "*.png")):
                fname = os.path.basename(png_path).lower()
                if fname == "shime1.png":
                    continue
                im = Image.open(png_path)
                cleaned = clean_chibi_sticker(im)
                cleaned.save(png_path)
            # Replace shime1.png with clean stand.png so no thumbnail ever shows the solid green/pink text square
            stand_p = os.path.join(folder, "stand.png")
            shime_p = os.path.join(folder, "shime1.png")
            if os.path.exists(stand_p):
                Image.open(stand_p).save(shime_p)
            ensure_complete_skin_frames(folder)
        print(f"Cleaned all Chibi frames for {char_name}")

    # 2. Clean Bocchi halo & ensure all frame names exist
    bocchi_dir = os.path.join(ASSETS_SKINS, "Bocchi")
    if os.path.isdir(bocchi_dir):
        for png_path in glob.glob(os.path.join(bocchi_dir, "*.png")):
            im = Image.open(png_path)
            cleaned = clean_bocchi_halo(im)
            cleaned.save(png_path)
        ensure_complete_skin_frames(bocchi_dir, is_bocchi=True)
        print("Cleaned and completed all Bocchi frames")

    # 3. Fix Konata guitar frames & ensure all frame names exist
    konata_dir = os.path.join(ASSETS_SKINS, "Konata")
    if os.path.isdir(konata_dir):
        build_konata_guitar_frames(konata_dir)
        ensure_complete_skin_frames(konata_dir, is_konata=True)
        print("Fixed Konata guitar frames and completed all Konata frames")

    # 4. Regenerate all res/drawable/doki_*.png and docs/characters/*.png with clean cutouts!
    # Classic / Chibi Stand & Snug
    for char_name in ["Monika", "Sayori", "Natsuki", "Yuri"]:
        lower = char_name.lower()
        stand_src = os.path.join(ASSETS_SKINS, char_name, "stand.png")
        snug_src = os.path.join(ASSETS_SKINS, char_name, "snug.png")

        classic_img = make_clean_chibi_showcase(stand_src, size=(256, 256))
        snug_img = make_clean_chibi_showcase(snug_src, size=(256, 256))

        classic_img.save(os.path.join(RES_DRAWABLE, f"doki_{lower}.png"))
        snug_img.save(os.path.join(RES_DRAWABLE, f"doki_{lower}_snug.png"))

        classic_img.save(os.path.join(DOCS_CHARS, f"{lower}_classic.png"))
        snug_img.save(os.path.join(DOCS_CHARS, f"{lower}_snug.png"))

    # Bocchi & Konata drawables and docs/characters
    bocchi_stand = make_clean_chibi_showcase(os.path.join(bocchi_dir, "stand1.png"), size=(256, 256))
    bocchi_guitar = make_clean_chibi_showcase(os.path.join(bocchi_dir, "guitar1.png"), size=(256, 256))
    bocchi_stand.save(os.path.join(RES_DRAWABLE, "doki_bocchi.png"))
    bocchi_guitar.save(os.path.join(RES_DRAWABLE, "doki_bocchi_snug.png"))
    bocchi_stand.save(os.path.join(DOCS_CHARS, "bocchi_stand.png"))
    bocchi_guitar.save(os.path.join(DOCS_CHARS, "bocchi_guitar.png"))

    konata_stand = make_clean_chibi_showcase(os.path.join(konata_dir, "stand1.png"), size=(256, 256))
    konata_guitar = make_clean_chibi_showcase(os.path.join(konata_dir, "guitar1.png"), size=(256, 256))
    konata_stand.save(os.path.join(RES_DRAWABLE, "doki_konata.png"))
    konata_guitar.save(os.path.join(RES_DRAWABLE, "doki_konata_snug.png"))
    konata_stand.save(os.path.join(DOCS_CHARS, "konata_stand.png"))
    konata_guitar.save(os.path.join(DOCS_CHARS, "konata_guitar.png"))

    # HD School & Casual full-body unclipped cutouts
    hd_map = [
        ("monika_school", "doki_monika_school.png", "monika escuela/Mon1.webp"),
        ("sayori_school", "doki_sayori_school.png", "sayori escuela/Say4.webp"),
        ("sayori_casual", "doki_sayori_casual.png", "sayori casual/Yori1.webp"),
        ("natsuki_school", "doki_natsuki_school.png", "natsuki escuela/Schoola.webp"),
        ("natsuki_casual", "doki_natsuki_casual.png", "natsuki casual/Casual1.webp"),
        ("yuri_school", "doki_yuri_school.png", "yuri escuela/Yuri_school_1.webp"),
        ("yuri_casual", "doki_yuri_casual.png", "yuri casual/Casualclothes1-0.webp"),
    ]
    for doc_key, draw_name, webp_rel in hd_map:
        cutout = extract_hd_cutout(webp_rel, canvas_size=(420, 560), fade_bottom=True)
        cutout.save(os.path.join(RES_DRAWABLE, draw_name))
        cutout.save(os.path.join(DOCS_CHARS, f"{doc_key}.png"))
        print(f"Generated clean full HD cutout: {doc_key}")

    # Clean up temporary test files if present
    for tmp in glob.glob(os.path.join(DOCS_CHARS, "test_*_clean.png")):
        try:
            os.remove(tmp)
        except OSError:
            pass

    # 5. Also fix docs/screenshots/landscape_stage_visualizer.png if it has portrait black bars
    land_shot = os.path.join(DOCS_SCREENSHOTS, "landscape_stage_visualizer.png")
    if os.path.exists(land_shot):
        im = Image.open(land_shot).convert("RGB")
        if im.height > im.width:
            arr = np.array(im)
            # Find non-black rows (mean brightness > 8)
            row_means = arr.mean(axis=(1, 2))
            non_black = np.where(row_means > 8.0)[0]
            if len(non_black) > 100:
                y0, y1 = int(non_black.min()), int(non_black.max()) + 1
                cropped_land = im.crop((0, y0, im.width, y1))
                cropped_land.save(land_shot)
                print(f"Cropped black bars from landscape_stage_visualizer.png -> {cropped_land.size}")


if __name__ == "__main__":
    main()
