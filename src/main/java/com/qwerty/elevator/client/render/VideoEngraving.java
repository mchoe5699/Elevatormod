package com.qwerty.elevator.client.render;

import java.awt.geom.FlatteningPathIterator;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.util.HashMap;
import java.util.Map;

/** Rounded, narrow engraved numerals, separate from Minecraft's bitmap UI font. */
public final class VideoEngraving {
    private VideoEngraving() {}
    private static final Map<Character, Path2D.Double> GLYPHS = new HashMap<>();
    static {
        Path2D.Double p;
        p=g('0'); p.moveTo(.5,.05); p.curveTo(.05,.05,.05,.95,.5,.95); p.curveTo(.95,.95,.95,.05,.5,.05);
        p=g('1'); p.moveTo(.25,.72); p.lineTo(.55,.95); p.lineTo(.55,.05);
        p=g('2'); p.moveTo(.15,.76); p.curveTo(.15,1.05,.88,1.04,.84,.73); p.curveTo(.82,.51,.31,.36,.14,.05); p.lineTo(.87,.05);
        p=g('3'); p.moveTo(.15,.88); p.curveTo(.74,1.17,1.08,.55,.48,.53); p.curveTo(1.08,.55,.87,-.15,.14,.14);
        p=g('4'); p.moveTo(.70,.05); p.lineTo(.70,.95); p.lineTo(.10,.30); p.lineTo(.91,.30);
        p=g('5'); p.moveTo(.85,.95); p.lineTo(.20,.95); p.lineTo(.16,.55); p.curveTo(.91,.85,1.04,-.13,.14,.12);
        p=g('6'); p.moveTo(.82,.88); p.curveTo(.08,1.25,-.02,.05,.5,.05); p.curveTo(1.07,.05,.94,.84,.19,.48);
        p=g('7'); p.moveTo(.12,.95); p.lineTo(.88,.95); p.lineTo(.35,.05);
        p=g('8'); p.moveTo(.50,.51); p.curveTo(-.07,.57,.05,.98,.50,.95); p.curveTo(.98,.98,1.06,.53,.50,.51); p.curveTo(-.10,.51,-.02,.02,.50,.05); p.curveTo(1.02,.02,1.10,.51,.50,.51);
        p=g('9'); p.moveTo(.18,.12); p.curveTo(.92,-.25,1.02,.95,.50,.95); p.curveTo(-.07,.95,.06,.16,.81,.52);
        p=g('B'); p.moveTo(.15,.05); p.lineTo(.15,.95); p.lineTo(.48,.95); p.curveTo(1.02,.95,1.02,.52,.48,.52); p.lineTo(.15,.52); p.moveTo(.48,.52); p.curveTo(1.10,.52,1.10,.05,.48,.05); p.lineTo(.15,.05);
        p=g('F'); p.moveTo(.2,.05); p.lineTo(.2,.95); p.lineTo(.85,.95); p.moveTo(.2,.52); p.lineTo(.7,.52);
        p=g('-'); p.moveTo(.2,.5); p.lineTo(.8,.5);
    }
    private static Path2D.Double g(char ch) { Path2D.Double p=new Path2D.Double(); GLYPHS.put(ch,p); return p; }

    public static void text(Draw d, String text, double cx, double cy, double z, double height,
                             double maxWidth, int color, int light) {
        double pitch=height*.70, width=pitch*text.length();
        if(width>maxWidth) { height*=maxWidth/width; pitch=height*.70; width=maxWidth; }
        double x=cx-width/2+pitch*.05, y=cy-height/2;
        for(char ch:text.toUpperCase().toCharArray()) {
            Path2D.Double glyph=GLYPHS.get(ch);
            if(glyph==null) { d.textFit(String.valueOf(ch),x+pitch/2,cy,z,height,pitch,color,light); x+=pitch; continue; }
            PathIterator it=new FlatteningPathIterator(glyph.getPathIterator(null),.025);
            double[] c=new double[6]; double px=0,py=0;
            while(!it.isDone()) {
                int kind=it.currentSegment(c);
                if(kind==PathIterator.SEG_LINETO) VideoMesh.line(d,x+px*pitch*.86,y+py*height,x+c[0]*pitch*.86,y+c[1]*height,z,height*.10,color,light);
                px=c[0]; py=c[1]; it.next();
            }
            x+=pitch;
        }
    }

    private static int braille(char c) {
        return switch(c) {
            case '1' -> 0b000001; case '2' -> 0b000011; case '3' -> 0b001001;
            case '4' -> 0b011001; case '5' -> 0b010001; case '6' -> 0b001011;
            case '7' -> 0b011011; case '8' -> 0b010011; case '9' -> 0b001010;
            case '0' -> 0b011010; case 'B' -> 0b000011; default -> 0;
        };
    }
    public static void braille(Draw d,String label,double cx,double cy,double size,double z,int light) {
        String chars="#"+label.toUpperCase(); double pitch=size*.12, cell=size*.30;
        double start=cx-(chars.length()-1)*cell/2-pitch/2;
        for(int i=0;i<chars.length();i++) {
            int bits=chars.charAt(i)=='#'?0b111100:braille(chars.charAt(i));
            for(int dot=0;dot<6;dot++) if((bits&(1<<dot))!=0) {
                double x=start+i*cell+(dot/3)*pitch, y=cy+(1-dot%3)*pitch;
                VideoMesh.lathe(d,x,y,new double[][]{{size*.027,z},{size*.020,z+size*.009},{0,z+size*.012}},0xD4D6D1,light,false);
            }
        }
    }
}
