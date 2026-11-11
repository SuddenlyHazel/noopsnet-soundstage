// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.soundstage.client;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.world.phys.AABB;

/** Builds the real vertex buffer without launching a game client or opening a window. */
public final class EffectsGeometryProbe {
    public static void main(String[] args){
        try(var bytes=new ByteBufferBuilder(4096)){
            var builder=new BufferBuilder(bytes,VertexFormat.Mode.QUADS,DefaultVertexFormat.NEW_ENTITY);
            EmissiveGeometry.box(builder,new PoseStack().last(),new AABB(0,0,0,1,1,1),0xff4020);
            try(var mesh=builder.buildOrThrow()){
                var data=mesh.vertexBuffer().order(java.nio.ByteOrder.nativeOrder());
                if(mesh.drawState().vertexCount()!=24)throw new AssertionError("Lens face count");
                int stride=DefaultVertexFormat.NEW_ENTITY.getVertexSize();
                for(int i=0;i<24;i++){
                    int p=i*stride;
                    if(Byte.toUnsignedInt(data.get(p+12))!=255||Byte.toUnsignedInt(data.get(p+13))!=64||Byte.toUnsignedInt(data.get(p+14))!=32||Byte.toUnsignedInt(data.get(p+15))!=255)throw new AssertionError("RGB/alpha lost");
                    if(data.getShort(p+28)!=240||data.getShort(p+30)!=240)throw new AssertionError("Not full bright");
                    float nx=data.get(p+32)/127f,ny=data.get(p+33)/127f,nz=data.get(p+34)/127f;
                    if(Math.abs(nx*nx+ny*ny+nz*nz-1)>.01)throw new AssertionError("Invalid normal");
                    if(i%4==0){
                        var a=point(data,p);var b=point(data,p+stride);var c=point(data,p+2*stride);
                        if(b.subtract(a).cross(c.subtract(a)).dot(new net.minecraft.world.phys.Vec3(nx,ny,nz))<=0)throw new AssertionError("Inward winding");
                    }
                }
            }
        }
        for(var direction:java.util.List.of(new net.minecraft.world.phys.Vec3(0,0,1),new net.minecraft.world.phys.Vec3(0,1,0),new net.minecraft.world.phys.Vec3(1,-1,1).normalize())){
            try(var bytes=new ByteBufferBuilder(4096)){
                var builder=new BufferBuilder(bytes,VertexFormat.Mode.QUADS,DefaultVertexFormat.NEW_ENTITY);
                EmissiveGeometry.beam(builder,new PoseStack().last(),net.minecraft.world.phys.Vec3.ZERO,direction.scale(10),direction,1,.5f,.25f);
                try(var mesh=builder.buildOrThrow()){
                    if(mesh.drawState().vertexCount()!=64)throw new AssertionError("Beam face count");
                    var data=mesh.vertexBuffer().order(java.nio.ByteOrder.nativeOrder());int stride=DefaultVertexFormat.NEW_ENTITY.getVertexSize();
                    for(int i=0;i<64;i+=4){
                        int p=i*stride;var a=point(data,p);var b=point(data,p+stride);var c=point(data,p+2*stride);
                        var n=new net.minecraft.world.phys.Vec3(data.get(p+32)/127f,data.get(p+33)/127f,data.get(p+34)/127f);
                        if(b.subtract(a).cross(c.subtract(a)).dot(n)<=0)throw new AssertionError("Beam culled from outside");
                        if(Byte.toUnsignedInt(data.get(p+15))!=(i<32?51:255))throw new AssertionError("Halo/core opacity");
                        if(!Double.isFinite(n.length())||Math.abs(n.length()-1)>.02)throw new AssertionError("Vertical beam normal");
                    }
                }
            }
        }
        System.out.println("PASS: complete NEW_ENTITY vertices; RGB, light, lens and laser winding, halo/core alpha and vertical/diagonal normals verified. No game launched.");
    }
    private static net.minecraft.world.phys.Vec3 point(java.nio.ByteBuffer b,int p){return new net.minecraft.world.phys.Vec3(b.getFloat(p),b.getFloat(p+4),b.getFloat(p+8));}
}
