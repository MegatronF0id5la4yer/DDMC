import os
import sys
import json
import math
import bpy

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


def hex_to_rgb(hex_str):
    h = hex_str.lstrip("#")
    return tuple(int(h[i:i + 2], 16) / 255.0 for i in (0, 2, 4))


def create_character_material(name, tex_path, accent_rgb):
    mat = bpy.data.materials.new(name=f"Mat_{name}")
    nodes = mat.node_tree.nodes
    links = mat.node_tree.links
    nodes.clear()

    out_node = nodes.new(type="ShaderNodeOutputMaterial")
    out_node.location = (500, 0)

    bsdf = nodes.new(type="ShaderNodeBsdfPrincipled")
    bsdf.location = (200, 0)
    bsdf.inputs["Roughness"].default_value = 0.38

    tex_node = nodes.new(type="ShaderNodeTexImage")
    tex_node.location = (-300, 100)
    img = bpy.data.images.load(tex_path)
    tex_node.image = img
    tex_node.interpolation = "Linear"
    tex_node.extension = "EXTEND"

    links.new(tex_node.outputs["Color"], bsdf.inputs["Base Color"])
    links.new(bsdf.outputs["BSDF"], out_node.inputs["Surface"])
    return mat


def create_pedestal_material(name, accent_rgb):
    mat = bpy.data.materials.new(name=f"Pedestal_{name}")
    mat.diffuse_color = (accent_rgb[0], accent_rgb[1], accent_rgb[2], 1.0)
    nodes = mat.node_tree.nodes
    links = mat.node_tree.links
    nodes.clear()

    out_node = nodes.new(type="ShaderNodeOutputMaterial")
    bsdf = nodes.new(type="ShaderNodeBsdfPrincipled")
    bsdf.inputs["Base Color"].default_value = (accent_rgb[0], accent_rgb[1], accent_rgb[2], 1.0)
    bsdf.inputs["Roughness"].default_value = 0.25
    bsdf.inputs["Metallic"].default_value = 0.65
    links.new(bsdf.outputs["BSDF"], out_node.inputs["Surface"])

    ring_img = bpy.data.images.new(f"RingTex_{name}", width=8, height=8)
    pixels = [accent_rgb[0], accent_rgb[1], accent_rgb[2], 1.0] * 64
    ring_img.pixels = pixels
    tex_node = nodes.new(type="ShaderNodeTexImage")
    tex_node.image = ring_img
    links.new(tex_node.outputs["Color"], bsdf.inputs["Base Color"])

    return mat


def build_rigged_hd_character(data):
    char_id = data["char_id"]
    tex_path = data["tex_path"]
    accent_rgb = hex_to_rgb(data["color_hex"])
    verts = [tuple(v) for v in data["verts"]]
    faces = [tuple(f) for f in data["faces"]]
    uvs = data["uvs"]
    weights = data["weights"]

    mesh = bpy.data.meshes.new(f"{char_id}_mesh")
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

    mesh_obj = bpy.data.objects.new(f"{char_id}_body", mesh)
    bpy.context.collection.objects.link(mesh_obj)

    mat = create_character_material(char_id, tex_path, accent_rgb)
    mesh.materials.append(mat)

    bpy.context.view_layer.objects.active = mesh_obj
    bpy.ops.object.mode_set(mode="EDIT")
    bpy.ops.mesh.select_all(action="SELECT")
    bpy.ops.mesh.normals_make_consistent(inside=False)
    bpy.ops.object.mode_set(mode="OBJECT")

    custom_normals = []
    for v in mesh.vertices:
        gn = v.normal
        sign_y = -1.0 if v.co.y <= 0.001 else 1.0
        nx = gn.x * 0.18
        ny = gn.y * 0.18 + (sign_y * 0.90) * 0.82
        nz = gn.z * 0.18 + 0.44 * 0.82
        length = max(1e-6, math.sqrt(nx * nx + ny * ny + nz * nz))
        custom_normals.append((nx / length, ny / length, nz / length))
    mesh.normals_split_custom_set_from_vertices(custom_normals)

    arm_data = bpy.data.armatures.new(f"{char_id}_rig_data")
    arm_obj = bpy.data.objects.new(f"{char_id}_rig", arm_data)
    bpy.context.collection.objects.link(arm_obj)
    bpy.context.view_layer.objects.active = arm_obj
    bpy.ops.object.mode_set(mode="EDIT")

    eb = arm_data.edit_bones

    def add_bone(bname, head_pos, tail_pos, parent_name=None, connect=False):
        b = eb.new(bname)
        b.head = head_pos
        b.tail = tail_pos
        b.roll = 0.0
        if parent_name and parent_name in eb:
            b.parent = eb[parent_name]
            b.use_connect = connect
        return b

    # Full-Body Non-Chibi Anime proportions (Z in 0.04 .. 1.92):
    add_bone("root", (0.0, 0.0, 0.0), (0.0, 0.0, 0.25), None)
    add_bone("hips", (0.0, 0.0, 0.64), (0.0, 0.0, 0.92), "root")
    add_bone("spine", (0.0, 0.0, 0.92), (0.0, 0.0, 1.42), "hips")
    add_bone("head", (0.0, 0.0, 1.42), (0.0, 0.0, 1.90), "spine")
    add_bone("hair_l", (-0.18, 0.0, 1.62), (-0.28, 0.0, 0.85), "head")
    add_bone("hair_r", (0.18, 0.0, 1.62), (0.30, 0.0, 0.78), "head")
    add_bone("arm_l", (-0.14, 0.0, 1.32), (-0.28, 0.0, 0.62), "spine")
    add_bone("arm_r", (0.14, 0.0, 1.32), (0.28, 0.0, 0.62), "spine")

    # Articulated 2-segment Left & Right Legs (Thigh -> Knee -> Shin/Foot)
    add_bone("thigh_l", (-0.075, 0.0, 0.64), (-0.070, 0.0, 0.37), "hips")
    add_bone("shin_l", (-0.070, 0.0, 0.37), (-0.070, 0.0, 0.04), "thigh_l", connect=True)
    add_bone("thigh_r", (0.075, 0.0, 0.64), (0.070, 0.0, 0.37), "hips")
    add_bone("shin_r", (0.070, 0.0, 0.37), (0.070, 0.0, 0.04), "thigh_r", connect=True)

    bpy.ops.object.mode_set(mode="OBJECT")

    for bname, vw_list in weights.items():
        vg = mesh_obj.vertex_groups.new(name=bname)
        for v_idx, w in vw_list:
            if w > 0.001:
                vg.add([int(v_idx)], float(w), "REPLACE")

    mesh_obj.parent = arm_obj
    mod = mesh_obj.modifiers.new(name="Armature", type="ARMATURE")
    mod.object = arm_obj

    bpy.ops.mesh.primitive_torus_add(
        major_radius=0.38,
        minor_radius=0.018,
        major_segments=36,
        minor_segments=12,
        location=(0.0, 0.0, 0.022),
    )
    ring_obj = bpy.context.active_object
    ring_obj.name = f"{char_id}_pedestal"
    ring_mat = create_pedestal_material(char_id, accent_rgb)
    ring_obj.data.materials.append(ring_mat)
    ring_obj.parent = arm_obj

    return arm_obj, mesh_obj


def setup_camera_and_workbench():
    scene = bpy.context.scene

    cam_data = bpy.data.cameras.new("StageCam")
    cam_data.lens = 56.0
    cam_obj = bpy.data.objects.new("StageCam", cam_data)
    bpy.context.collection.objects.link(cam_obj)
    cam_obj.location = (0.0, -3.55, 0.98)
    cam_obj.rotation_euler = (math.radians(89.2), 0.0, 0.0)
    scene.camera = cam_obj

    scene.render.engine = "BLENDER_WORKBENCH"
    scene.render.film_transparent = True
    scene.render.resolution_x = 360
    scene.render.resolution_y = 480
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = "WEBP"
    scene.render.image_settings.color_mode = "RGBA"
    scene.render.image_settings.quality = 92

    scene.view_settings.view_transform = "Standard"
    scene.view_settings.look = "None"

    shading = scene.display.shading
    shading.light = "STUDIO"
    shading.color_type = "TEXTURE"
    shading.show_specular_highlight = False
    shading.show_cavity = False
    shading.show_backface_culling = False
    scene.display.render_aa = "8"


def reset_pose(arm_obj):
    for pb in arm_obj.pose.bones:
        pb.rotation_mode = "XYZ"
        pb.location = (0.0, 0.0, 0.0)
        pb.rotation_euler = (0.0, 0.0, 0.0)
        pb.scale = (1.0, 1.0, 1.0)


def animate_and_render_sequence(arm_obj, char_id, anim_name, num_frames, out_dir):
    scene = bpy.context.scene
    scene.frame_start = 0
    scene.frame_end = num_frames - 1

    pb = arm_obj.pose.bones
    os.makedirs(out_dir, exist_ok=True)

    arm_obj.animation_data_clear()
    arm_obj.animation_data_create()
    action = bpy.data.actions.new(name=f"{char_id}_{anim_name}")
    arm_obj.animation_data.action = action

    bone_names = ["hips", "spine", "head", "hair_l", "hair_r", "arm_l", "arm_r", "thigh_l", "shin_l", "thigh_r", "shin_r"]

    for f in range(num_frames):
        scene.frame_set(f)
        reset_pose(arm_obj)
        phase = (f / float(num_frames)) * 2.0 * math.pi
        s1 = math.sin(phase)
        c1 = math.cos(phase)
        s2 = math.sin(phase * 2.0)

        if anim_name == "idle":
            pb["hips"].location = (0.018 * s1, 0.016 * abs(s1), 0.0)
            pb["hips"].rotation_euler = (math.radians(2.0 * c1), math.radians(16.0 * s1), math.radians(2.2 * s1))
            pb["spine"].rotation_euler = (math.radians(2.5 * s1), math.radians(5.0 * c1), math.radians(-1.8 * s1))
            pb["head"].rotation_euler = (math.radians(3.5 * c1), math.radians(-5.0 * s1), math.radians(4.0 * s1))
            pb["arm_l"].rotation_euler = (math.radians(8.0 * s1), math.radians(4.0 * c1), math.radians(2.0 * c1))
            pb["arm_r"].rotation_euler = (math.radians(-8.0 * s1), math.radians(-4.0 * c1), math.radians(-2.0 * c1))
            pb["thigh_l"].rotation_euler = (math.radians(6.0 * s1), 0.0, math.radians(-3.5 * max(0.0, s1)))
            pb["shin_l"].rotation_euler = (math.radians(-12.0 * max(0.0, s1)), 0.0, math.radians(5.0 * max(0.0, s1)))
            pb["thigh_r"].rotation_euler = (math.radians(-6.0 * s1), 0.0, math.radians(3.5 * max(0.0, -s1)))
            pb["shin_r"].rotation_euler = (math.radians(-12.0 * max(0.0, -s1)), 0.0, math.radians(-5.0 * max(0.0, -s1)))

        elif anim_name == "walk":
            pb["hips"].location = (0.032 * s1, 0.036 * abs(c1), 0.0)
            pb["hips"].rotation_euler = (math.radians(3.0), math.radians(16.0 * s1), math.radians(3.5 * s1))
            pb["spine"].rotation_euler = (math.radians(2.5 * c1), math.radians(-6.5 * s1), math.radians(-3.0 * s1))
            pb["head"].rotation_euler = (math.radians(-2.5 * c1), math.radians(-4.0 * s1), math.radians(3.5 * c1))
            pb["thigh_l"].rotation_euler = (math.radians(28.0 * s1), 0.0, math.radians(-6.0 * max(0.0, s1)))
            pb["shin_l"].rotation_euler = (math.radians(-34.0 * max(0.0, -c1)), 0.0, math.radians(8.0 * max(0.0, -c1)))
            pb["thigh_r"].rotation_euler = (math.radians(-28.0 * s1), 0.0, math.radians(6.0 * max(0.0, -s1)))
            pb["shin_r"].rotation_euler = (math.radians(-34.0 * max(0.0, c1)), 0.0, math.radians(-8.0 * max(0.0, c1)))
            pb["arm_l"].rotation_euler = (math.radians(-20.0 * s1), math.radians(5.0 * c1), math.radians(2.5))
            pb["arm_r"].rotation_euler = (math.radians(20.0 * s1), math.radians(-5.0 * c1), math.radians(-2.5))

        elif anim_name == "dance":
            # Full-Body Idol Dance: rhythmic hip sway + bounce, 3D turntable groove (+/- 22 deg),
            # wide side-step & heel-kick with real knee bend (`shin_l` / `shin_r`)!
            bounce = abs(s2)
            l_kick = max(0.0, s1)
            r_kick = max(0.0, -s1)
            pb["hips"].location = (0.068 * s1, 0.045 * bounce, 0.0)
            pb["hips"].rotation_euler = (math.radians(4.5 * s2), math.radians(22.0 * s1), math.radians(7.0 * s1))
            pb["spine"].rotation_euler = (math.radians(5.0 * c1), math.radians(-8.0 * c1), math.radians(-5.5 * s1))
            pb["head"].rotation_euler = (math.radians(6.0 * s2), math.radians(-6.0 * s1), math.radians(7.5 * s1))
            pb["arm_l"].rotation_euler = (math.radians(-24.0 * s1), math.radians(12.0 * c1), math.radians(5.0 + 4.5 * s2))
            pb["arm_r"].rotation_euler = (math.radians(24.0 * s1), math.radians(-12.0 * c1), math.radians(-5.0 - 4.5 * s2))

            pb["thigh_l"].rotation_euler = (
                math.radians(28.0 * l_kick - 8.0 * r_kick),
                math.radians(-8.0 * l_kick),
                math.radians(-14.0 * l_kick + 4.0 * r_kick),
            )
            pb["shin_l"].rotation_euler = (
                math.radians(-40.0 * l_kick - 6.0 * bounce),
                0.0,
                math.radians(20.0 * l_kick),
            )

            pb["thigh_r"].rotation_euler = (
                math.radians(28.0 * r_kick - 8.0 * l_kick),
                math.radians(8.0 * r_kick),
                math.radians(14.0 * r_kick - 4.0 * l_kick),
            )
            pb["shin_r"].rotation_euler = (
                math.radians(-40.0 * r_kick - 6.0 * bounce),
                0.0,
                math.radians(-20.0 * r_kick),
            )

        elif anim_name == "jump":
            t = f / float(num_frames)
            jump_arc = math.sin(t * math.pi)
            pb["hips"].location = (0.0, 0.18 * (jump_arc ** 1.25), 0.0)
            pb["hips"].rotation_euler = (math.radians(-6.0 * jump_arc), math.radians(25.0 * s1), math.radians(3.5 * s1))
            pb["spine"].rotation_euler = (math.radians(-5.5 * jump_arc), math.radians(8.0 * s1), 0.0)
            pb["head"].rotation_euler = (math.radians(-5.5 * jump_arc), math.radians(5.0 * c1), math.radians(4.5 * s1))
            pb["arm_l"].rotation_euler = (math.radians(-24.0 * jump_arc), math.radians(8.0 * jump_arc), math.radians(5.5 * jump_arc))
            pb["arm_r"].rotation_euler = (math.radians(-24.0 * jump_arc), math.radians(-8.0 * jump_arc), math.radians(-5.5 * jump_arc))
            pb["thigh_l"].rotation_euler = (math.radians(26.0 * jump_arc), 0.0, math.radians(-12.0 * jump_arc))
            pb["shin_l"].rotation_euler = (math.radians(-46.0 * jump_arc), 0.0, math.radians(18.0 * jump_arc))
            pb["thigh_r"].rotation_euler = (math.radians(22.0 * jump_arc), 0.0, math.radians(12.0 * jump_arc))
            pb["shin_r"].rotation_euler = (math.radians(-42.0 * jump_arc), 0.0, math.radians(-18.0 * jump_arc))

        elif anim_name == "action":
            l_kick = max(0.0, s1)
            r_kick = max(0.0, -s1)
            pb["hips"].location = (0.058 * s1, 0.050 * abs(s2), 0.0)
            pb["hips"].rotation_euler = (math.radians(5.5 * s1), math.radians(26.0 * s1), math.radians(6.5 * c1))
            pb["spine"].rotation_euler = (math.radians(5.5 * abs(s1)), math.radians(8.5 * c1), math.radians(-5.0 * s1))
            pb["head"].rotation_euler = (math.radians(6.5 * s2), math.radians(-8.0 * s1), math.radians(7.5 * c1))
            pb["arm_l"].rotation_euler = (math.radians(-26.0 * c1), math.radians(11.0 * s1), math.radians(5.5 * abs(s1)))
            pb["arm_r"].rotation_euler = (math.radians(26.0 * s2), math.radians(-11.0 * s1), math.radians(-5.5 * abs(c1)))
            pb["thigh_l"].rotation_euler = (math.radians(32.0 * l_kick), 0.0, math.radians(-15.0 * l_kick))
            pb["shin_l"].rotation_euler = (math.radians(-44.0 * l_kick), 0.0, math.radians(22.0 * l_kick))
            pb["thigh_r"].rotation_euler = (math.radians(32.0 * r_kick), 0.0, math.radians(15.0 * r_kick))
            pb["shin_r"].rotation_euler = (math.radians(-44.0 * r_kick), 0.0, math.radians(-22.0 * r_kick))

        for bname in bone_names:
            pb[bname].keyframe_insert(data_path="location", frame=f)
            pb[bname].keyframe_insert(data_path="rotation_euler", frame=f)

        frame_file = os.path.join(out_dir, f"{anim_name}_{f:02d}.webp")
        if os.path.exists(frame_file):
            try:
                os.remove(frame_file)
            except OSError:
                pass
        scene.render.filepath = frame_file
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
        print(f"[Blender 5.2 HD] Building 3D Full-Body rigged HD character: {char_id}")

        with open(char_entry["mesh_json"], "r", encoding="utf-8") as mf:
            mesh_data = json.load(mf)

        arm_obj, mesh_obj = build_rigged_hd_character(mesh_data)

        out_dir = char_entry["render_out_dir"]
        anims = [
            ("idle", 12),
            ("walk", 12),
            ("jump", 12),
            ("action", 12),
            ("dance", 12),
        ]
        for anim_name, n_frames in anims:
            print(f"  -> Rendering HD 3D animation '{anim_name}' ({n_frames} frames)...")
            animate_and_render_sequence(arm_obj, char_id, anim_name, n_frames, out_dir)

        glb_out = char_entry["glb_out_path"]
        os.makedirs(os.path.dirname(glb_out), exist_ok=True)
        bpy.ops.export_scene.gltf(
            filepath=glb_out,
            export_format="GLB",
            export_animations=True,
        )
        print(f"  -> Exported 3D glTF model: {glb_out}")


if __name__ == "__main__":
    main()
