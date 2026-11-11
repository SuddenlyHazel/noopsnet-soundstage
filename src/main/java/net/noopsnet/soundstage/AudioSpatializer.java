// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.soundstage;

/** Small stereo DSP stage: balanced spatial panning and a wall-occlusion low-pass. */
public final class AudioSpatializer {
    private double leftMemory,rightMemory;
    public static double[] gains(double volume,double pan){
        pan=Math.clamp(pan,-1,1);volume=Math.clamp(volume,0,1);
        return new double[]{volume*Math.sqrt(1-Math.max(0,pan)),volume*Math.sqrt(1+Math.min(0,pan))};
    }
    public void process(byte[] pcm,double volume,double pan,double occlusion){
        double[] gains=gains(volume,pan);occlusion=Math.clamp(occlusion,0,1);
        double cutoff=18000*(1-occlusion)+900*occlusion;
        double coefficient=1-Math.exp(-2*Math.PI*cutoff/48000);
        double attenuation=1-.65*occlusion;
        for(int i=0;i+3<pcm.length;i+=4){
            int l=(short)((pcm[i]&255)|(pcm[i+1]<<8));int r=(short)((pcm[i+2]&255)|(pcm[i+3]<<8));
            leftMemory+=coefficient*(l-leftMemory);rightMemory+=coefficient*(r-rightMemory);
            int a=(int)Math.clamp(Math.round(leftMemory*gains[0]*attenuation),-32768,32767);
            int b=(int)Math.clamp(Math.round(rightMemory*gains[1]*attenuation),-32768,32767);
            pcm[i]=(byte)a;pcm[i+1]=(byte)(a>>8);pcm[i+2]=(byte)b;pcm[i+3]=(byte)(b>>8);
        }
    }
    public void reset(){leftMemory=rightMemory=0;}
}
