package com.qwerty.elevator.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.phys.Vec3;

/** Curved hardware surfaces, with smooth normals rather than stacked square boxes. */
public final class VideoMesh {
    private VideoMesh() {}
    private static void vertex(Draw d, VertexConsumer vc, double x, double y, double z,
                               double nx, double ny, double nz, int rgb, int light) {
        TextureAtlasSprite sp = RenderUtil.white();
        RenderUtil.vertex(vc, d.ps.last().pose(), d.ps.last().normal(), (float) x, (float) y, (float) z,
                0xFF000000 | rgb, sp.getU(8), sp.getV(8), light, (float) nx, (float) ny, (float) nz);
    }

    private static int metal(double a, double radius) {
        // Polished metal bands follow the bevel, leaving the centre a brushed silver.
        double band = Math.pow(Math.max(0, Math.cos(a - 2.25)), 12);
        double dark = Math.pow(Math.max(0, Math.cos(a + 0.5)), 8);
        double f = Math.max(0.16, Math.min(1, 0.65 + band * 0.50 - dark * 0.48 + radius * 0.04));
        return RenderUtil.lerpColor(0x242A2D, 0xF6F6ED, (float) f);
    }

    /** Stamped square mounting shell: raised corners curve down into the circular button socket. */
    public static void saddleBezel(Draw d,double cx,double cy,double r,int light) {
        VertexConsumer vc=d.buf.getBuffer(RenderUtil.solid());
        for(int band=0;band<5;band++) for(int segment=0;segment<64;segment++) {
            double[][] uv={{(double)band/5,(double)segment/64},{(double)band/5,(double)(segment+1)/64},
                    {(double)(band+1)/5,(double)(segment+1)/64},{(double)(band+1)/5,(double)segment/64}};
            for(double[] q:uv) {
                double t=q[0], a=q[1]*Math.PI*2, ca=Math.cos(a),sa=Math.sin(a);
                double outer=r*1.12/Math.pow(Math.pow(Math.abs(ca),8)+Math.pow(Math.abs(sa),8),1.0/8);
                double rr=outer*(1-t)+r*1.005*t;
                double corner=Math.pow(Math.sin(2*a),2);
                double z=.002+r*(.10+.12*corner)*Math.sin(Math.PI*t);
                int color=metal(a+corner*.7,t);
                vertex(d,vc,cx+ca*rr,cy+sa*rr,z,ca*.18,sa*.18,.967,color,light);
            }
        }
    }

    /** Bow-front hall fixture, straight sides and bottom, with a gently arched top edge. */
    public static void archedPanel(Draw d,double cx,double halfWidth,double y0,double y1,
                                     double arch,double zBase,double bow,int rgb,int light,boolean steel) {
        VertexConsumer vc=d.buf.getBuffer(RenderUtil.solid());
        for(int ix=0;ix<24;ix++) for(int iy=0;iy<24;iy++) {
            double[][] uv={{ix/12.0-1,iy/24.0},{(ix+1)/12.0-1,iy/24.0},
                    {(ix+1)/12.0-1,(iy+1)/24.0},{ix/12.0-1,(iy+1)/24.0}};
            for(double[] q:uv) {
                double x=q[0],v=q[1];
                double bottom=y0+.003*Math.pow(Math.abs(x),16),top=y1-arch*x*x;
                double z=zBase+bow*(1-x*x),nx=2*bow*x/halfWidth,nl=Math.sqrt(1+nx*nx);
                int color=rgb;
                if(steel) {
                    float f=(float)(.58+.24*Math.cos(x*1.3)+.035*Math.sin(v*175));
                    if(Math.abs(x)>.90) f=(float)(.42+.52*Math.abs(Math.sin(x*23)));
                    color=RenderUtil.lerpColor(0x566267,0xF0F0E5,f);
                }
                vertex(d,vc,cx+x*halfWidth,bottom+(top-bottom)*v,z,nx/nl,0,1/nl,color,light);
            }
        }
    }

    /** Surface of revolution around +Z; profiles are outer-to-inner (radius, depth) pairs. */
    public static void lathe(Draw d, double cx, double cy, double[][] profile, int rgb, int light, boolean chrome) {
        VertexConsumer vc = d.buf.getBuffer(RenderUtil.solid());
        final int n = 48;
        for (int ring = 0; ring + 1 < profile.length; ring++) {
            double r0 = profile[ring][0], z0 = profile[ring][1];
            double r1 = profile[ring + 1][0], z1 = profile[ring + 1][1];
            double dz = z1 - z0, dr = r0 - r1, len = Math.hypot(dz, dr);
            if (len < 1e-9) continue;
            for (int i = 0; i < n; i++) {
                double a = 2 * Math.PI * i / n, b = 2 * Math.PI * (i + 1) / n;
                double[][] q = {{r0,a,z0},{r0,b,z0},{r1,b,z1},{r1,a,z1}};
                for (double[] p : q) {
                    double ca = Math.cos(p[1]), sa = Math.sin(p[1]);
                    int color = chrome ? metal(p[1], p[0]) : rgb;
                    vertex(d, vc, cx + p[0] * ca, cy + p[0] * sa, p[2],
                            ca * dz / len, sa * dz / len, dr / len, color, light);
                }
            }
        }
    }

    /** True rounded rectangle with an inset, raised bevel. */
    public static void plate(Draw d, double x0, double y0, double x1, double y1, double radius,
                              double z, double bevel, int rgb, int light, boolean chrome) {
        VertexConsumer vc = d.buf.getBuffer(RenderUtil.solid());
        double cx = (x0 + x1) / 2, cy = (y0 + y1) / 2;
        double hw = (x1 - x0) / 2, hh = (y1 - y0) / 2;
        radius = Math.max(0, Math.min(radius, Math.min(hw, hh)));
        double[][] outer = new double[36][2], inner = new double[36][2];
        for (int i = 0; i < 36; i++) {
            int corner = i / 9;
            double a = corner * Math.PI / 2 + (i % 9) * Math.PI / 16;
            double sx = corner == 0 || corner == 3 ? 1 : -1;
            double sy = corner < 2 ? 1 : -1;
            outer[i] = new double[]{cx + sx * (hw - radius) + Math.cos(a) * radius,
                    cy + sy * (hh - radius) + Math.sin(a) * radius};
            inner[i] = new double[]{cx + sx * (hw - radius) + Math.cos(a) * Math.max(0, radius - bevel),
                    cy + sy * (hh - radius) + Math.sin(a) * Math.max(0, radius - bevel)};
        }
        for (int i = 0; i < 36; i++) {
            int next = (i + 1) % 36;
            int c0 = chrome ? metal(i * Math.PI / 18, 0) : rgb;
            int c1 = chrome ? metal(next * Math.PI / 18, 0) : rgb;
            vertex(d,vc,outer[i][0],outer[i][1],z,0,0,1,c0,light);
            vertex(d,vc,outer[next][0],outer[next][1],z,0,0,1,c1,light);
            vertex(d,vc,inner[next][0],inner[next][1],z+bevel,0,0,1,c1,light);
            vertex(d,vc,inner[i][0],inner[i][1],z+bevel,0,0,1,c0,light);
            vertex(d,vc,cx,cy,z+bevel,0,0,1,rgb,light);
            vertex(d,vc,inner[i][0],inner[i][1],z+bevel,0,0,1,rgb,light);
            vertex(d,vc,inner[next][0],inner[next][1],z+bevel,0,0,1,rgb,light);
            vertex(d,vc,cx,cy,z+bevel,0,0,1,rgb,light);
        }
    }

    public static void disc(Draw d, double x, double y, double z, double r, int color, int light) {
        VertexConsumer vc=d.buf.getBuffer(RenderUtil.solid());
        for(int i=0;i<12;i++) {
            double a=2*Math.PI*i/12,b=2*Math.PI*(i+1)/12;
            vertex(d,vc,x,y,z,0,0,1,color,light);
            vertex(d,vc,x+r*Math.cos(a),y+r*Math.sin(a),z,0,0,1,color,light);
            vertex(d,vc,x+r*Math.cos(b),y+r*Math.sin(b),z,0,0,1,color,light);
            vertex(d,vc,x,y,z,0,0,1,color,light);
        }
    }

    /** Fine horizontal hairline grain and broad silver bands, without the block texture's large pixels. */
    public static void brushedPlate(Draw d,double x0,double y0,double x1,double y1,double z,int light) {
        VertexConsumer vc=d.buf.getBuffer(RenderUtil.solid());
        int rows=Math.max(32,Math.min(256,(int)((y1-y0)*180)));
        for(int row=0;row<rows;row++) {
            double a=(double)row/rows,b=(double)(row+1)/rows;
            double grain=Math.sin(row*7.13)*.012;
            float f=(float)(.57+.30*Math.exp(-Math.pow((a-.63)/.22,2))+grain);
            int left=RenderUtil.lerpColor(0x788384,0xECEDE4,f);
            int right=RenderUtil.lerpColor(0x788384,0xECEDE4,Math.min(1,f+.07f));
            vertex(d,vc,x0,y0+a*(y1-y0),z,0,0,1,left,light);
            vertex(d,vc,x1,y0+a*(y1-y0),z,0,0,1,right,light);
            vertex(d,vc,x1,y0+b*(y1-y0),z,0,0,1,right,light);
            vertex(d,vc,x0,y0+b*(y1-y0),z,0,0,1,left,light);
        }
    }

    public static void line(Draw d, double x0, double y0, double x1, double y1, double z,
                             double width, int color, int light) {
        double dx = x1-x0, dy = y1-y0, len = Math.hypot(dx,dy);
        if (len < 1e-9) return;
        double nx = -dy/len*width/2, ny = dx/len*width/2;
        VertexConsumer vc = d.buf.getBuffer(RenderUtil.solid());
        vertex(d,vc,x0-nx,y0-ny,z,0,0,1,color,light);
        vertex(d,vc,x1-nx,y1-ny,z,0,0,1,color,light);
        vertex(d,vc,x1+nx,y1+ny,z,0,0,1,color,light);
        vertex(d,vc,x0+nx,y0+ny,z,0,0,1,color,light);
        disc(d,x0,y0,z,width/2,color,light);
        disc(d,x1,y1,z,width/2,color,light);
    }

    public static void triangle(Draw d,double ax,double ay,double bx,double by,double cx,double cy,
                                  double z,int color,int light) {
        if((bx-ax)*(cy-ay)-(by-ay)*(cx-ax)<0) {
            double tx=bx,ty=by; bx=cx; by=cy; cx=tx; cy=ty;
        }
        VertexConsumer vc=d.buf.getBuffer(RenderUtil.solid());
        vertex(d,vc,ax,ay,z,0,0,1,color,light);
        vertex(d,vc,bx,by,z,0,0,1,color,light);
        vertex(d,vc,cx,cy,z,0,0,1,color,light);
        vertex(d,vc,ax,ay,z,0,0,1,color,light);
    }

    public static void tube(Draw d, Vec3 start, Vec3 end, double r, int rgb, int light, boolean chrome) {
        Vec3 axis = end.subtract(start).normalize();
        Vec3 u = axis.cross(Math.abs(axis.y) < 0.9 ? new Vec3(0,1,0) : new Vec3(1,0,0)).normalize();
        Vec3 v = axis.cross(u).normalize();
        VertexConsumer vc = d.buf.getBuffer(RenderUtil.solid());
        for (int i=0; i<32; i++) {
            double a=2*Math.PI*i/32, b=2*Math.PI*(i+1)/32;
            Vec3 na=u.scale(Math.cos(a)).add(v.scale(Math.sin(a)));
            Vec3 nb=u.scale(Math.cos(b)).add(v.scale(Math.sin(b)));
            Vec3[] points={start.add(na.scale(r)),start.add(nb.scale(r)),end.add(nb.scale(r)),end.add(na.scale(r))};
            Vec3[] normals={na,nb,nb,na};
            for(int j=0;j<4;j++) {
                Vec3 p=points[j], n=normals[j];
                int c=chrome?metal(j==0||j==3?a:b,r):RenderUtil.dim(rgb,(float)(0.78+0.22*Math.max(0,n.y)));
                vertex(d,vc,p.x,p.y,p.z,n.x,n.y,n.z,c,light);
            }
        }
    }
}
