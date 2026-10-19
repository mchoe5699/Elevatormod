package com.qwerty.elevator.client.render;

import com.qwerty.elevator.block.entity.ControllerBlockEntity;
import com.qwerty.elevator.elevator.ElevatorSettings;
import net.minecraft.world.phys.Vec3;

/** LG MGP hardware from the second reference. Advertising and wall attachments are omitted. */
public final class LgFixtures {
    private LgFixtures() {}
    private static final int RED=0xF31336;
    private static final int[] DIGITS={0x3F,0x06,0x5B,0x4F,0x66,0x6D,0x7D,0x07,0x7F,0x6F};
    // a,b,c,d,e,f,g, normalized to a narrow seven-segment digit.
    private static final double[][] SEG={{.12,1,.88,1},{.92,.94,.92,.55},{.92,.45,.92,.06},{.12,0,.88,0},{.08,.06,.08,.45},{.08,.55,.08,.94},{.12,.5,.88,.5}};
    private static void digits(Draw d,String text,double cx,double bottom,double height,double z) {
        double width=height*.53,advance=width*1.30,start=cx-(text.length()-1)*advance/2-width/2;
        for(int i=0;i<text.length();i++) {
            char ch=text.charAt(i);
            int bits=ch>='0'&&ch<='9'?DIGITS[ch-'0']:ch=='B'?0x7C:ch=='-'?0x40:0;
            if(bits==0) {d.textFit(String.valueOf(ch),start+i*advance+width/2,bottom+height/2,z,height,width,RED,RenderUtil.FULL_BRIGHT);continue;}
            for(int k=0;k<7;k++) if((bits&(1<<k))!=0) {
                double[] a=SEG[k];
                VideoMesh.line(d,start+i*advance+a[0]*width,bottom+a[1]*height,start+i*advance+a[2]*width,bottom+a[3]*height,z,height*.065,RED,RenderUtil.FULL_BRIGHT);
            }
        }
    }
    public static void indicator(Draw d,String name,int dir,boolean moving,float time,double x0,double y0,double x1,double y1,double z,int light,boolean powered) {
        VideoMesh.archedPanel(d,(x0+x1)/2,(x1-x0)/2,y0,y1,.006,z-.002,.002,0x08090B,light,false);
        if(!powered)return;
        double w=x1-x0,h=y1-y0;
        boolean vertical=h>w*1.1;
        double dh=Math.min(h*(vertical?.25:.56),w*.65/Math.max(1,(name==null?1:name.length())*.7));
        digits(d,name==null?"":name,(x0+x1)/2,y0+h*(vertical?.48:.22),dh,z+.002);
        if(dir!=0) {
            double cy=y0+h*(vertical?.80:.5),cx=vertical?(x0+x1)/2:x0+w*.15,r=Math.min(w*.12,h*.13);
            int color=RED;
            // Direction remains fixed; the number changes discretely, without scrolling.
            VideoMesh.line(d,cx-r,cy-dir*r*.4,cx,cy+dir*r*.5,z+.002,r*.22,color,RenderUtil.FULL_BRIGHT);
            VideoMesh.line(d,cx,cy+dir*r*.5,cx+r,cy-dir*r*.4,z+.002,r*.22,color,RenderUtil.FULL_BRIGHT);
        }
    }
    public static void button(Draw d,double x,double y,double size,boolean on,boolean pressed,String label,int light) {
        double r=size/2,z=pressed?.006:.010;
        VideoMesh.lathe(d,x,y,new double[][]{{r*1.08,.001},{r*1.06,.006},{r*.90,.009}},0xC5C8C4,light,true);
        VideoMesh.lathe(d,x,y,new double[][]{{r*.90,z},{r*.80,z+.001},{0,z+.0018}},0x171B19,light,false);
        d.textFit(label,x,y,z+.0025,size*.45,size*.78,on?RED:0xD5D7CC,on?RenderUtil.FULL_BRIGHT:light);
        if(on) VideoMesh.lathe(d,x,y,new double[][]{{r*.94,z+.002},{r*.86,z+.002}},RED,RenderUtil.FULL_BRIGHT,false);
    }
    public static void cop(Draw d,double w,double h,double displayBottom,ElevatorSettings s,int light) {
        VideoMesh.brushedPlate(d,.01,.01,w-.01,h-.01,.002,light);
        // Factory specification, service key and alarm area; no pasted notices.
        d.textFit("LG",w/2,displayBottom-.025,.004,.035,w*.5,0x333936,light);
        d.textFit(s.capacityPersons+"명  "+s.capacityKg+"kg",w/2,displayBottom-.052,.004,.018,w*.86,0x343A36,light);
    }
    public static void rail(Draw d,Vec3 start,Vec3 end,int light) {
        // Flattened steel rail with three parallel rolled ribs.
        for(double y:new double[]{-.018,0,.018}) VideoMesh.tube(d,start.add(0,y,0),end.add(0,y,0),.012,0xBEC2B8,light,true);
        var axis=end.subtract(start).normalize();
        for(var p:new Vec3[]{start.add(axis.scale(.09)),end.subtract(axis.scale(.09))})
            VideoMesh.tube(d,p,p.add(axis.z!=0?-.06:0,0,axis.x!=0?.06:0),.012,0x939C97,light,true);
    }
    public static void doorPattern(Draw d,double x0,double y0,double x1,double y1,double z,int light) {
        VideoMesh.brushedPlate(d,x0,y0,x1,y1,z,light);
        z+=.0003;
        // Fine etched horizontal stipple bands travel with each door leaf.
        for(double y=y0+.16;y<y1-.10;y+=.34) for(int row=0;row<9;row++) {
            double yy=y+row*.022;if(yy>=y1)break;
            for(double x=x0+.012+(row%2)*.007;x<x1-.012;x+=.018)
                d.color(0x717C76,light,x,yy,z,Math.min(x1,x+.005),yy+.0015,z+.0002);
        }
    }
    public static void hall(Draw d,ControllerBlockEntity c,boolean up,boolean down,boolean upLit,boolean downLit,int light,float time) {
        VideoMesh.plate(d,.39,.08,.61,1.44,.004,.001,.008,0xB6BAB5,light,true);
        indicator(d,c==null?"--":c.displayName(),c==null?0:c.displayDir(),false,time,.411,.58,.589,1.42,.011,light,c==null||c.isMainPower());
        d.color(0x252B29,light,.411,.11,.011,.589,.56,.012);
        if(up) triangleButton(d,.5,down?.405:.32,true,upLit,light);
        if(down) triangleButton(d,.5,up?.205:.32,false,downLit,light);
    }
    private static void triangleButton(Draw d,double x,double y,boolean up,boolean on,int light) {
        double s=up?1:-1;
        VideoMesh.triangle(d,x-.047,y-s*.026,x,y+s*.043,x+.047,y-s*.026,.016,0x929B96,light);
        VideoMesh.triangle(d,x-.036,y-s*.021,x,y+s*.032,x+.036,y-s*.021,.018,on?RED:0xD2D6CC,on?RenderUtil.FULL_BRIGHT:light);
    }
}
