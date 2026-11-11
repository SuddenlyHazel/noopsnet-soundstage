// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.soundstage.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.noopsnet.soundstage.Soundstage;

/** Standard emissive material, with complete vertices for Iris shader replacement. */
final class EmissiveGeometry {
    private static final class Material {
        static final RenderType TYPE=RenderType.eyes(ResourceLocation.fromNamespaceAndPath(Soundstage.ID,"textures/effect/white.png"));
    }
    static RenderType type(){return Material.TYPE;}

    static void beam(VertexConsumer out,PoseStack.Pose pose,Vec3 start,Vec3 end,Vec3 direction,float r,float g,float b){
        var side=direction.cross(Math.abs(direction.y)>.95?new Vec3(1,0,0):new Vec3(0,1,0)).normalize();
        var up=direction.cross(side).normalize();
        // Draw the transparent halo before the core. Dim RGB, not alpha: shader
        // packs commonly discard alpha below .1, which must not cut off fades.
        for(int layer=0;layer<2;layer++)for(int n=0;n<8;n++){
            double a=n*Math.PI/4,angle=(n+1)*Math.PI/4,radius=layer==0?.045:.012;
            var u=side.scale(Math.cos(a)*radius).add(up.scale(Math.sin(a)*radius));
            var v=side.scale(Math.cos(angle)*radius).add(up.scale(Math.sin(angle)*radius));
            quad(out,pose,start.add(u),start.add(v),end.add(v),end.add(u),u.add(v).normalize(),r,g,b,layer==0?.2f:1f);
        }
    }

    static void quad(VertexConsumer out,PoseStack.Pose pose,Vec3 a,Vec3 b,Vec3 c,Vec3 d,Vec3 normal,float r,float g,float blue){
        quad(out,pose,a,b,c,d,normal,r,g,blue,1f);
    }
    static void quad(VertexConsumer out,PoseStack.Pose pose,Vec3 a,Vec3 b,Vec3 c,Vec3 d,Vec3 normal,float r,float g,float blue,float alpha){
        vertex(out,pose,a,0,0,normal,r,g,blue,alpha);vertex(out,pose,b,1,0,normal,r,g,blue,alpha);
        vertex(out,pose,c,1,1,normal,r,g,blue,alpha);vertex(out,pose,d,0,1,normal,r,g,blue,alpha);
    }
    private static void vertex(VertexConsumer out,PoseStack.Pose pose,Vec3 p,float u,float v,Vec3 n,float r,float g,float b,float alpha){
        out.addVertex(pose.pose(),(float)p.x,(float)p.y,(float)p.z).setColor(r,g,b,alpha)
            .setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
            .setNormal(pose,(float)n.x,(float)n.y,(float)n.z);
    }
    static void box(VertexConsumer out,PoseStack.Pose pose,AABB b,int rgb){
        float r=((rgb>>16)&255)/255f,g=((rgb>>8)&255)/255f,blue=(rgb&255)/255f;
        var p000=new Vec3(b.minX,b.minY,b.minZ);var p001=new Vec3(b.minX,b.minY,b.maxZ);
        var p010=new Vec3(b.minX,b.maxY,b.minZ);var p011=new Vec3(b.minX,b.maxY,b.maxZ);
        var p100=new Vec3(b.maxX,b.minY,b.minZ);var p101=new Vec3(b.maxX,b.minY,b.maxZ);
        var p110=new Vec3(b.maxX,b.maxY,b.minZ);var p111=new Vec3(b.maxX,b.maxY,b.maxZ);
        quad(out,pose,p000,p010,p110,p100,new Vec3(0,0,-1),r,g,blue);
        quad(out,pose,p001,p101,p111,p011,new Vec3(0,0,1),r,g,blue);
        quad(out,pose,p000,p001,p011,p010,new Vec3(-1,0,0),r,g,blue);
        quad(out,pose,p100,p110,p111,p101,new Vec3(1,0,0),r,g,blue);
        quad(out,pose,p000,p100,p101,p001,new Vec3(0,-1,0),r,g,blue);
        quad(out,pose,p010,p011,p111,p110,new Vec3(0,1,0),r,g,blue);
    }
}
