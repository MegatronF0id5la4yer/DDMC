import os
import sys
import json
import math
import time
import bpy
from mathutils import Matrix, Vector

# Executed inside Blender 5.2:
# blender.exe --background --python scripts/blender_rig_and_render_hd.py -- <manifest_json_path>


def clear_scene_objects():
    for obj in list(bpy.data.objects):
        bpy.data.objects.remove(obj, do_unlink=True)
    for m in list(bpy.data.meshes):
        bpy.data.meshes.remove(m, do_unlink=True)
    for a in list(bpy.data.armatures):
        bpy.data.armatures.remove(a, do_unlink=True)
    for mat in list(bpy.data.materials):
        bpy.data.materials.remove(mat, do_unlink=True)
    for img in list(bpy.data.images):
        bpy.data.images.remove(img, do_unlink=True)


def create_character_material(name, tex_path):
    mat = bpy.data.materials.new(name=f"Mat_{name}")
    if hasattr(mat, "surface_render_method"):
        mat.surface_render_method = "BLENDED"
    if hasattr(mat, "blend_method"):
        mat.blend_method = "BLEND"

    nodes = mat.node_tree.nodes
    links = mat.node_tree.links
    nodes.clear()

    out_node = nodes.new(type="ShaderNodeOutputMaterial")
    out_node.location = (500, 0)

    bsdf = nodes.new(type="ShaderNodeBsdfPrincipled")
    bsdf.location = (200, 0)
    bsdf.inputs["Roughness"].default_value = 0.5

    tex_node = nodes.new(type="ShaderNodeTexImage")
    tex_node.location = (-300, 100)
    img = bpy.data.images.load(tex_path)
    tex_node.image = img
    tex_node.interpolation = "Linear"
    tex_node.extension = "CLIP"

    links.new(tex_node.outputs["Color"], bsdf.inputs["Base Color"])
    links.new(tex_node.outputs["Alpha"], bsdf.inputs["Alpha"])
    links.new(bsdf.outputs["BSDF"], out_node.inputs["Surface"])
    return mat


def build_rigged_hd_character(mesh_data):
    char_id = mesh_data["char_id"]

    # 1. Create 3D Full-Body Biped Armature
    arm_data = bpy.data.armatures.new(f"Armature_{char_id}")
    arm_obj = bpy.data.objects.new(f"Armature_{char_id}", arm_data)
    bpy.context.collection.objects.link(arm_obj)
    bpy.context.view_layer.objects.active = arm_obj
    bpy.ops.object.mode_set(mode="EDIT")

    eb = arm_data.edit_bones

    b_root = eb.new("root")
    b_root.head = (0.0, 0.0, 0.0)
    b_root.tail = (0.0, 0.0, 0.25)

    # Hips / Pelvis (Z = 0.68 .. 0.96)
    b_hips = eb.new("hips")
    b_hips.head = (0.0, 0.0, 0.68)
    b_hips.tail = (0.0, 0.0, 0.96)
    b_hips.parent = b_root

    # Upper Torso / Chest (Z = 0.96 .. 1.40)
    b_spine = eb.new("spine")
    b_spine.head = (0.0, 0.0, 0.96)
    b_spine.tail = (0.0, 0.0, 1.40)
    b_spine.parent = b_hips
    b_spine.use_connect = True

    # Head & Hair (Z = 1.40 .. 1.92)
    b_head = eb.new("head")
    b_head.head = (0.0, 0.0, 1.40)
    b_head.tail = (0.0, 0.0, 1.92)
    b_head.parent = b_spine
    b_head.use_connect = True

    # Articulated Left Leg: Thigh (0.68 -> 0.46) & Shin/Knee (0.46 -> 0.05)
    b_thigh_l = eb.new("thigh_l")
    b_thigh_l.head = (-0.072, 0.032, 0.68)
    b_thigh_l.tail = (-0.068, 0.032, 0.46)
    b_thigh_l.parent = b_hips

    b_shin_l = eb.new("shin_l")
    b_shin_l.head = (-0.068, 0.032, 0.46)
    b_shin_l.tail = (-0.066, 0.032, 0.05)
    b_shin_l.parent = b_thigh_l
    b_shin_l.use_connect = True

    # Articulated Right Leg: Thigh (0.68 -> 0.46) & Shin/Knee (0.46 -> 0.05)
    b_thigh_r = eb.new("thigh_r")
    b_thigh_r.head = (0.072, 0.032, 0.68)
    b_thigh_r.tail = (0.068, 0.032, 0.46)
    b_thigh_r.parent = b_hips

    b_shin_r = eb.new("shin_r")
    b_shin_r.head = (0.068, 0.032, 0.46)
    b_shin_r.tail = (0.066, 0.032, 0.05)
    b_shin_r.parent = b_thigh_r
    b_shin_r.use_connect = True

    # Optional articulated arms (Yuri has both hands behind her back: no arm rig).
    arm_joints = mesh_data.get("arm_joints")
    if arm_joints:
        for side_key, suffix in (("left", "l"), ("right", "r")):
            j = arm_joints[side_key]
            piv = Vector((j["pivot"][0], 0.0, j["pivot"][1]))
            elb = Vector((j["elbow"][0], 0.0, j["elbow"][1]))
            wri = Vector((j["wrist"][0], 0.0, j["wrist"][1]))
            b_u = eb.new(f"uarm_{suffix}")
            b_u.head = piv
            b_u.tail = elb
            b_u.parent = b_spine
            b_f = eb.new(f"farm_{suffix}")
            b_f.head = elb
            b_f.tail = wri
            b_f.parent = b_u
            b_f.use_connect = True

    bpy.ops.object.mode_set(mode="OBJECT")

    bone_names = ["root", "hips", "spine", "head", "thigh_l", "shin_l", "thigh_r", "shin_r"]
    if arm_joints:
        bone_names += ["uarm_l", "farm_l", "uarm_r", "farm_r"]

    # 2. Build the 3 Layered Meshes ('leg_l', 'leg_r', 'upper')
    part_objs = []
    for part in mesh_data["parts"]:
        pname = part["part_name"]
        verts = [tuple(v) for v in part["vertices"]]
        faces = [tuple(f) for f in part["faces"]]
        uvs = part["uvs"]
        weights = part["weights"]

        m_name = f"Mesh_{char_id}_{pname}"
        mesh = bpy.data.meshes.new(m_name)
        mesh.from_pydata(verts, [], faces)
        mesh.update()

        for poly in mesh.polygons:
            poly.use_smooth = True

        uv_layer = mesh.uv_layers.new(name="UVMap")
        mesh.uv_layers.active = uv_layer
        for poly in mesh.polygons:
            for loop_idx in poly.loop_indices:
                v_idx = mesh.loops[loop_idx].vertex_index
                uv_layer.data[loop_idx].uv = uvs[v_idx]

        mesh_obj = bpy.data.objects.new(m_name, mesh)
        bpy.context.collection.objects.link(mesh_obj)

        mat = create_character_material(f"{char_id}_{pname}", part["texture_path"])
        mesh.materials.append(mat)

        vgroups = {}
        for bname in bone_names:
            vgroups[bname] = mesh_obj.vertex_groups.new(name=bname)

        for v_idx, w_dict in enumerate(weights):
            for bname, w_val in w_dict.items():
                if bname in vgroups and w_val > 0.0001:
                    vgroups[bname].add([v_idx], float(w_val), "REPLACE")

        mesh_obj.parent = arm_obj
        mod = mesh_obj.modifiers.new(name="Armature", type="ARMATURE")
        mod.object = arm_obj
        mod.use_vertex_groups = True
        part_objs.append(mesh_obj)

    return arm_obj, part_objs


def setup_camera_and_workbench():
    scene = bpy.context.scene
    # Use Workbench with FLAT texture lighting + Standard sRGB view transform
    # so colors are 100% pure, vibrant Satchely DDLC colors (no AgX grey dimming!)
    scene.render.engine = "BLENDER_WORKBENCH"
    scene.render.film_transparent = True
    scene.render.resolution_x = 440
    scene.render.resolution_y = 640
    scene.render.resolution_percentage = 100

    scene.view_settings.view_transform = "Standard"
    scene.view_settings.look = "None"
    scene.view_settings.exposure = 0.0
    scene.view_settings.gamma = 1.0

    scene.render.image_settings.file_format = "WEBP"
    scene.render.image_settings.color_mode = "RGBA"
    scene.render.image_settings.quality = 95

    shading = scene.display.shading
    shading.light = "FLAT"
    shading.color_type = "TEXTURE"
    shading.show_backface_culling = False
    shading.show_shadows = False
    shading.show_cavity = False
    shading.show_object_outline = False

    cam_data = bpy.data.cameras.new("StageCam")
    cam_data.type = "PERSP"
    cam_data.lens = 62.0
    cam_obj = bpy.data.objects.new("StageCam", cam_data)
    bpy.context.collection.objects.link(cam_obj)
    cam_obj.location = (0.0, -3.55, 1.00)
    cam_obj.rotation_euler = (math.radians(89.5), 0.0, 0.0)
    scene.camera = cam_obj


ARM_BONES = ("uarm_l", "farm_l", "uarm_r", "farm_r")


def reset_pose(arm_obj):
    for pb in arm_obj.pose.bones:
        if pb.name in ARM_BONES:
            pb.rotation_mode = "QUATERNION"
            pb.rotation_quaternion = (1.0, 0.0, 0.0, 0.0)
        else:
            pb.rotation_mode = "XYZ"
            pb.rotation_euler = (0.0, 0.0, 0.0)
        pb.location = (0.0, 0.0, 0.0)
        pb.scale = (1.0, 1.0, 1.0)


def _world_y_quat(pbone, deg_ccw):
    """
    Local-space quaternion that rotates `pbone` about the WORLD Y axis (the camera
    view axis) by `deg_ccw` degrees, counter-clockwise as seen on screen.
    Valid while the parent chain is at rest; extra parent motion is inherited.
    """
    rest = pbone.bone.matrix_local.to_3x3()
    rot = Matrix.Rotation(-math.radians(deg_ccw), 3, "Y")
    return (rest.inverted() @ rot @ rest).to_quaternion()


def set_arm(pb, side, abd, flex, front=0.04):
    """
    side: 'l' (screen-left) or 'r' (screen-right).
    abd : shoulder swing in degrees. 0 = hanging down, +90 = straight out sideways,
          +180 = straight up. Negative swings the arm across the body.
    flex: extra elbow bend in degrees. Positive keeps curling outward/up,
          negative folds the forearm toward the body.
    """
    u = pb.get(f"uarm_{side}")
    if u is None:
        return
    s = 1.0 if side == "r" else -1.0
    u.rotation_quaternion = _world_y_quat(u, s * abd)
    f = pb[f"farm_{side}"]
    f.rotation_quaternion = _world_y_quat(f, s * flex)
    rest = u.bone.matrix_local.to_3x3()
    u.location = rest.inverted() @ Vector((0.0, -front, 0.0))


def _K(**kw):
    base = dict(hx=0.0, hz=0.0, yh=0.0, th=0.0, ys=0.0, ts=0.0, hy=0.0, ht=0.0,
                aL=6.0, fL=4.0, aR=6.0, fR=4.0, sl=0.0, sr=0.0)
    base.update(kw)
    return base


# 12 key poses, one every 4 frames (48-frame loop). Modelled on the reference
# TikTok-style idol dance: twisting side-to-side, crossed-arm pointing, both arms
# overhead, hip sway with hands near the face.
DANCE_KEYS = [
    # --- A: twist + cute rhythmic gesture ---------------------------------
    _K(hx=-0.012, yh=-8, ys=-14, ts=3, hy=10, ht=-5, aL=14, fL=12, aR=2, fR=6, sl=0.7),
    _K(hx=0.012, yh=8, ys=14, ts=-3, hy=-10, ht=5, aL=2, fL=6, aR=14, fR=12, sr=0.7),
    _K(hx=-0.012, hz=-0.012, yh=-8, ys=-14, ts=3, hy=10, ht=-5, aL=16, fL=15, aR=4, fR=8, sl=0.6),
    _K(hx=0.012, hz=-0.012, yh=8, ys=14, ts=-3, hy=-10, ht=5, aL=4, fL=8, aR=16, fR=15, sr=0.6),
    # --- B: rhythmic hip bounce & arm sway -------------------------------
    _K(hz=-0.022, aL=14, fL=16, aR=14, fR=16, sl=0.3, sr=0.3, ht=-4),
    _K(hz=0.012, yh=6, ys=8, aL=8, fL=10, aR=16, fR=18, sl=0.0, sr=0.0, ht=4, hy=-6),
    _K(hz=-0.022, yh=-6, ys=-8, aL=16, fL=18, aR=8, fR=10, sl=0.3, sr=0.3, ht=-4, hy=6),
    _K(hz=0.008, aL=10, fL=8, aR=10, fR=8, ht=0),
    # --- C: cute hip sway & head tilt ------------------------------------
    _K(hx=0.014, yh=10, ys=16, ts=-5, th=3, hy=-8, aL=6, fL=8, aR=15, fR=14, sr=0.8),
    _K(hx=-0.014, yh=-10, ys=-16, ts=5, th=-3, hy=8, aL=15, fL=14, aR=6, fR=8, sl=0.8),
    _K(hx=0.010, hz=-0.010, yh=8, ys=12, ts=-4, hy=-6, aL=4, fL=6, aR=16, fR=16, sr=0.5),
    _K(hx=-0.010, hz=-0.010, yh=-8, ys=-12, ts=4, hy=6, aL=16, fL=16, aR=4, fR=6, sl=0.5),
]


def _smooth(t):
    return t * t * (3.0 - 2.0 * t)


def _dance_pose(f, n):
    u = (f / float(n)) * len(DANCE_KEYS)
    i = int(math.floor(u)) % len(DANCE_KEYS)
    j = (i + 1) % len(DANCE_KEYS)
    t = _smooth(u - math.floor(u))
    a, b = DANCE_KEYS[i], DANCE_KEYS[j]
    return {k: a[k] * (1.0 - t) + b[k] * t for k in a}


def apply_dance(pb, f, n):
    p = _dance_pose(f, n)
    rad = math.radians
    pb["hips"].location = (p["hx"], 0.0, p["hz"])
    pb["hips"].rotation_euler = (0.0, rad(p["yh"]), rad(p["th"]))
    pb["spine"].rotation_euler = (0.0, rad(p["ys"]), rad(p["ts"]))
    pb["head"].rotation_euler = (0.0, rad(p["hy"]), rad(p["ht"]))
    sl, sr = p["sl"], p["sr"]
    pb["thigh_l"].location = (0.0, 0.0, 0.014 * sl)
    pb["thigh_l"].rotation_euler = (rad(10.0 * sl), 0.0, rad(-13.5 * sl))
    pb["shin_l"].rotation_euler = (rad(-18.0 * sl), 0.0, rad(24.0 * sl))
    pb["thigh_r"].location = (0.0, 0.0, 0.014 * sr)
    pb["thigh_r"].rotation_euler = (rad(10.0 * sr), 0.0, rad(13.5 * sr))
    pb["shin_r"].rotation_euler = (rad(-18.0 * sr), 0.0, rad(-24.0 * sr))
    set_arm(pb, "l", p["aL"], p["fL"])
    set_arm(pb, "r", p["aR"], p["fR"])


def apply_arms_for(pb, anim_name, phase, s1, c1, s2):
    """Arm motion for the non-dance animations."""
    if anim_name == "idle":
        set_arm(pb, "l", 5.0 + 2.5 * s1, 5.0 + 3.0 * c1)
        set_arm(pb, "r", 5.0 - 2.5 * s1, 5.0 - 3.0 * c1)
    elif anim_name == "walk":
        set_arm(pb, "l", 9.0 + 7.0 * max(0.0, s1) - 3.0 * max(0.0, -s1), 18.0 + 10.0 * max(0.0, s1))
        set_arm(pb, "r", 9.0 + 7.0 * max(0.0, -s1) - 3.0 * max(0.0, s1), 18.0 + 10.0 * max(0.0, -s1))
    elif anim_name == "jump":
        up = max(0.0, math.sin(phase * 0.5))
        set_arm(pb, "l", 6.0 + 10.0 * up, 6.0 + 12.0 * up)
        set_arm(pb, "r", 6.0 + 10.0 * up, 6.0 + 12.0 * up)
    elif anim_name == "action":
        pump_l = 0.5 + 0.5 * s1
        pump_r = 0.5 - 0.5 * s1
        set_arm(pb, "l", 8.0 + 10.0 * pump_l, 6.0 + 12.0 * pump_l)
        set_arm(pb, "r", 8.0 + 10.0 * pump_r, 6.0 + 12.0 * pump_r)


def animate_and_render_sequence(arm_obj, anim_name, num_frames, out_dir):
    scene = bpy.context.scene
    os.makedirs(out_dir, exist_ok=True)
    pb = arm_obj.pose.bones

    scene.frame_start = 0
    scene.frame_end = num_frames - 1

    if arm_obj.animation_data:
        arm_obj.animation_data_clear()
    arm_obj.animation_data_create()
    action = bpy.data.actions.new(name=f"{arm_obj.name}_{anim_name}")
    arm_obj.animation_data.action = action

    # Note on bone local axes in Blender:
    # - Upward bones (hips, spine, head):
    #   local Y = World +Z (vertical twist/turn)
    #   local Z = World -Y (screen-plane tilt left/right)
    #   local X = World +X (forward/back nod)
    # - Downward leg bones (thigh_l, shin_l, thigh_r, shin_r):
    #   local Y = World -Z (leg twist)
    #   local Z = World +Y (screen-plane swing: negative = left, positive = right)
    #   local X = World +X (forward/back swing)
    for f in range(num_frames):
        scene.frame_set(f)
        reset_pose(arm_obj)
        phase = (f / float(num_frames)) * 2.0 * math.pi
        s1 = math.sin(phase)
        c1 = math.cos(phase)
        s2 = math.sin(phase * 2.0)
        c2 = math.cos(phase * 2.0)

        if anim_name == "idle":
            pb["hips"].location = (0.008 * s1, 0.0, 0.006 * s2)
            pb["hips"].rotation_euler = (0.0, math.radians(2.5 * s1), math.radians(1.2 * c1))
            pb["spine"].rotation_euler = (math.radians(1.0 * s2), math.radians(-1.5 * s1), math.radians(-1.0 * c1))
            pb["head"].rotation_euler = (math.radians(1.5 * c1), math.radians(2.0 * s1), math.radians(2.2 * s1))
            pb["thigh_l"].rotation_euler = (0.0, 0.0, math.radians(-1.8 * max(0.0, s1)))
            pb["shin_l"].rotation_euler = (0.0, 0.0, math.radians(3.2 * max(0.0, s1)))
            pb["thigh_r"].rotation_euler = (0.0, 0.0, math.radians(1.8 * max(0.0, -s1)))
            pb["shin_r"].rotation_euler = (0.0, 0.0, math.radians(-3.2 * max(0.0, -s1)))

        elif anim_name == "dance":
            apply_dance(pb, f, num_frames)

        elif anim_name == "walk":
            step_l = max(0.0, s1)
            step_r = max(0.0, -s1)
            pb["hips"].location = (0.018 * c1, 0.0, 0.016 * abs(s2) - 0.005)
            pb["hips"].rotation_euler = (math.radians(2.0 * s2), math.radians(7.0 * s1), math.radians(2.5 * c1))
            pb["spine"].rotation_euler = (math.radians(2.0), math.radians(-5.0 * s1), math.radians(-2.0 * c1))
            pb["head"].rotation_euler = (math.radians(2.0 * s2), math.radians(3.0 * s1), math.radians(2.2 * s1))

            pb["thigh_l"].location = (0.0, 0.0, 0.012 * step_l)
            pb["thigh_l"].rotation_euler = (math.radians(14.0 * s1), 0.0, math.radians(-6.5 * step_l + 2.5 * step_r))
            pb["shin_l"].rotation_euler = (math.radians(-18.0 * step_l), 0.0, math.radians(12.0 * step_l))

            pb["thigh_r"].location = (0.0, 0.0, 0.012 * step_r)
            pb["thigh_r"].rotation_euler = (math.radians(-14.0 * s1), 0.0, math.radians(6.5 * step_r - 2.5 * step_l))
            pb["shin_r"].rotation_euler = (math.radians(-18.0 * step_r), 0.0, math.radians(-12.0 * step_r))

        elif anim_name == "jump":
            jump_arc = max(0.0, math.sin(phase * 0.5))
            crouch = -0.022 * math.cos(phase) if jump_arc < 0.25 else 0.0
            pb["hips"].location = (0.0, 0.0, 0.080 * (jump_arc ** 1.3) + crouch)
            pb["hips"].rotation_euler = (math.radians(-3.0 * s1), math.radians(7.0 * s1), math.radians(2.5 * c1))
            pb["spine"].rotation_euler = (math.radians(-4.0 * jump_arc), 0.0, math.radians(2.0 * s1))
            pb["head"].rotation_euler = (math.radians(-3.5 * jump_arc), math.radians(3.5 * s1), math.radians(3.0 * s1))

            # Cute double anime knee-tuck in mid-air
            pb["thigh_l"].location = (0.0, 0.0, 0.016 * jump_arc)
            pb["thigh_l"].rotation_euler = (math.radians(12.0 * jump_arc), 0.0, math.radians(-11.0 * jump_arc))
            pb["shin_l"].rotation_euler = (math.radians(-20.0 * jump_arc), 0.0, math.radians(20.0 * jump_arc))

            pb["thigh_r"].location = (0.0, 0.0, 0.016 * jump_arc)
            pb["thigh_r"].rotation_euler = (math.radians(12.0 * jump_arc), 0.0, math.radians(11.0 * jump_arc))
            pb["shin_r"].rotation_euler = (math.radians(-20.0 * jump_arc), 0.0, math.radians(-20.0 * jump_arc))

        elif anim_name == "action":
            # High-energy idol chorus step & kick
            kick_l = max(0.0, s1)
            kick_r = max(0.0, -s1)
            pb["hips"].location = (0.034 * s1, 0.0, 0.026 * abs(s2) - 0.008)
            pb["hips"].rotation_euler = (
                math.radians(3.0 * s2),
                math.radians(8.5 * c1),
                math.radians(5.2 * s1),
            )
            pb["spine"].rotation_euler = (
                math.radians(3.5 * c2),
                math.radians(-5.0 * c1),
                math.radians(-4.5 * s1),
            )
            pb["head"].rotation_euler = (
                math.radians(4.0 * s2),
                math.radians(5.0 * s1),
                math.radians(5.5 * c1),
            )
            pb["thigh_l"].location = (0.0, 0.0, 0.018 * kick_l)
            pb["thigh_l"].rotation_euler = (
                math.radians(12.0 * kick_l),
                0.0,
                math.radians(-16.5 * kick_l + 2.5 * kick_r),
            )
            pb["shin_l"].rotation_euler = (
                math.radians(-22.0 * kick_l),
                0.0,
                math.radians(28.0 * kick_l),
            )
            pb["thigh_r"].location = (0.0, 0.0, 0.018 * kick_r)
            pb["thigh_r"].rotation_euler = (
                math.radians(12.0 * kick_r),
                0.0,
                math.radians(16.5 * kick_r - 2.5 * kick_l),
            )
            pb["shin_r"].rotation_euler = (
                math.radians(-22.0 * kick_r),
                0.0,
                math.radians(-28.0 * kick_r),
            )

        if anim_name != "dance":
            apply_arms_for(pb, anim_name, phase, s1, c1, s2)

        for bname in ["hips", "spine", "head", "thigh_l", "shin_l", "thigh_r", "shin_r"]:
            pb[bname].keyframe_insert(data_path="location", frame=f)
            pb[bname].keyframe_insert(data_path="rotation_euler", frame=f)
        for bname in ARM_BONES:
            if bname in pb:
                pb[bname].keyframe_insert(data_path="location", frame=f)
                pb[bname].keyframe_insert(data_path="rotation_quaternion", frame=f)

        os.makedirs(out_dir, exist_ok=True)
        frame_file = os.path.join(out_dir, f"{anim_name}_{f:02d}.webp")
        scene.render.filepath = frame_file
        rendered = False
        for attempt in range(5):
            try:
                if os.path.exists(frame_file):
                    try:
                        os.remove(frame_file)
                    except Exception:
                        pass
                bpy.ops.render.render(write_still=True)
                rendered = True
                break
            except Exception as e:
                print(f"[Blender 5.2 HD] Render retry {attempt+1}/5 on {frame_file}: {e}")
                time.sleep(0.35)
        if not rendered:
            bpy.ops.render.render(write_still=True)


def main():
    argv = sys.argv
    if "--" not in argv:
        print("Usage: blender --background --python blender_rig_and_render_hd.py -- <manifest.json>")
        return
    manifest_path = argv[argv.index("--") + 1]
    with open(manifest_path, "r", encoding="utf-8") as f:
        manifest = json.load(f)

    bpy.ops.wm.read_factory_settings(use_empty=True)

    for char_entry in manifest["characters"]:
        clear_scene_objects()
        setup_camera_and_workbench()

        char_id = char_entry["char_id"]
        print(f"[Blender 5.2 HD] Building 3-Layer Full-Body 3D character: {char_id}")

        with open(char_entry["mesh_json"], "r", encoding="utf-8") as mf:
            mesh_data = json.load(mf)

        arm_obj, _ = build_rigged_hd_character(mesh_data)

        out_dir = char_entry["render_out_dir"]
        anims = [
            ("idle", 12),
            ("walk", 12),
            ("dance", 48),
            ("jump", 12),
            ("action", 12),
        ]
        for anim_name, frames in anims:
            animate_and_render_sequence(arm_obj, anim_name, frames, out_dir)

        glb_path = char_entry.get("glb_out_path")
        if glb_path:
            os.makedirs(os.path.dirname(glb_path), exist_ok=True)
            try:
                bpy.ops.export_scene.gltf(
                    filepath=glb_path,
                    export_format="GLB",
                    export_animations=True,
                )
            except Exception as e:
                print(f"[Blender 5.2 HD] Note: GLB export skipped or warning: {e}")


if __name__ == "__main__":
    main()
