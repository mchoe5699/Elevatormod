package com.qwerty.elevator.client.render;

import com.qwerty.elevator.block.entity.ControllerBlockEntity;
import com.qwerty.elevator.block.entity.DeviceBlockEntity;

/** Fixtures observed in the supplied video; geometry faces +Z like the shared COP layout. */
public final class VideoFixtures {
    private VideoFixtures() {}

    private static final java.util.Map<Character,String[]> LED = java.util.Map.ofEntries(
            java.util.Map.entry('0',new String[]{"01110","10001","10001","10001","10001","10001","01110"}),
            java.util.Map.entry('1',new String[]{"00100","01100","00100","00100","00100","00100","01110"}),
            java.util.Map.entry('2',new String[]{"01110","10001","00001","00010","00100","01000","11111"}),
            java.util.Map.entry('3',new String[]{"01110","10001","00001","00110","00001","10001","01110"}),
            java.util.Map.entry('4',new String[]{"00010","00110","01010","10010","11111","00010","00010"}),
            java.util.Map.entry('5',new String[]{"11111","10000","11110","00001","00001","10001","01110"}),
            java.util.Map.entry('6',new String[]{"01110","10000","10000","11110","10001","10001","01110"}),
            java.util.Map.entry('7',new String[]{"11111","00001","00010","00100","00100","00100","00100"}),
            java.util.Map.entry('8',new String[]{"01110","10001","10001","01110","10001","10001","01110"}),
            java.util.Map.entry('9',new String[]{"01110","10001","10001","01111","00001","00001","01110"}),
            java.util.Map.entry('B',new String[]{"11110","10001","10001","11110","10001","10001","11110"}),
            java.util.Map.entry('F',new String[]{"11111","10000","10000","11110","10000","10000","10000"}),
            java.util.Map.entry('-',new String[]{"00000","00000","00000","11111","00000","00000","00000"}),
            java.util.Map.entry('^',new String[]{"00100","01010","10001","00100","00100","00100","00100"}),
            java.util.Map.entry('v',new String[]{"00100","00100","00100","00100","10001","01010","00100"}));

    /** LED dots are circular, with a warm core, and no lit rectangular off-cell grid. */
    private static void leds(Draw d,String text,double x,double y,double pitch,double z,
                              double clipBottom,double clipTop,int light) {
        for(int ci=0;ci<text.length();ci++) {
            String[] rows=LED.get(text.charAt(ci));
            if(rows==null) continue;
            for(int row=0;row<7;row++) for(int col=0;col<5;col++) if(rows[row].charAt(col)=='1') {
                double px=x+(ci*6+col+.5)*pitch, py=y+(6-row+.5)*pitch;
                if(py-pitch*.35<clipBottom || py+pitch*.35>clipTop) continue;
                VideoMesh.disc(d,px,py,z,pitch*.40,0x703019,light);
                VideoMesh.disc(d,px,py,z+.00008,pitch*.29,0xFF7725,RenderUtil.FULL_BRIGHT);
                VideoMesh.disc(d,px,py,z+.00016,pitch*.12,0xFFD190,RenderUtil.FULL_BRIGHT);
            }
        }
    }

    public static void indicator(Draw d, String name, int dir, boolean moving, float time,
                                 double x0, double y0, double x1, double y1, double z, int light, boolean powered) {
        double w=x1-x0,h=y1-y0;
        VideoMesh.plate(d,x0,y0,x1,y1,.002,z-.004,.001,0x090D1C,light,false);
        // Printed circular manufacturer emblem and wordmark are separate from the LED grid.
        double logoX=x0+w*.085, logoY=y0+h*.33, radius=h*.15;
        VideoMesh.lathe(d,logoX,logoY,new double[][]{{radius,z+.0002},{radius*.83,z+.0002}},0x999FA7,light,false);
        VideoMesh.line(d,logoX-radius*.55,logoY-radius*.50,logoX+radius*.55,logoY+radius*.50,z+.0004,radius*.16,0x999FA7,light);
        d.textFit("HYUNDAI",x0+w*.89,y0+h*.22,z+.0004,h*.08,w*.16,0x9CA1A9,light);
        if(!powered) return;
        String text=name==null?"":name;
        double pitch=Math.min(h/9,w*.45/Math.max(5,text.length()*6-1));
        double sy=y0+(h-7*pitch)/2;
        String arrow=dir>0?"^":dir<0?"v":"";
        double offset=com.qwerty.elevator.elevator.VideoDisplayState.arrowRows(time,dir,moving)*pitch;
        leds(d,arrow,x0+w*.26,sy+offset,pitch,z+.001,y0+h*.09,y1-h*.09,light);
        if(offset!=0) leds(d,arrow,x0+w*.26,sy+offset+Integer.signum(dir)*8*pitch,pitch,z+.001,y0+h*.09,y1-h*.09,light);
        if(text.chars().allMatch(c->LED.containsKey((char)c)))
            leds(d,text,x0+w*.45,sy,pitch,z+.001,y0,y1,light);
        else d.textFit(text,x0+w*.60,y0+h/2,z+.001,h*.7,w*.43,0xFF7725,RenderUtil.FULL_BRIGHT);
    }

    private static void disc(Draw d, double x, double y, double radius, double z0, double z1, int rgb, int light) {
        d.ps.pushPose();
        d.ps.translate(x, y, 0);
        RenderUtil.discZ(d.buf, d.ps, RenderUtil.white(), 0xFF000000 | rgb, light, radius, z0, z1, 24);
        d.ps.popPose();
    }

    public static void button(Draw d,double x,double y,double size,boolean on,int lit,String label,int light) {
        button(d,x,y,size,on,false,lit,label,light);
    }

    public static void button(Draw d,double x,double y,double size,boolean on,boolean pressed,int lit,String label,int light) {
        double r=size/2, travel=pressed?size*.025:0;
        VideoMesh.saddleBezel(d,x,y,r,light);
        // Rounded chrome lip, black separation, luminous red annulus, shallow domed metal face.
        VideoMesh.lathe(d,x,y,new double[][]{{r*1.02,.004},{r*1.01,.007},{r*.96,.010},{r*.91,.010}},0xC6C9C2,light,true);
        VideoMesh.lathe(d,x,y,new double[][]{{r*.915,.010},{r*.86,.010}},on?lit:0x292C29,on?RenderUtil.FULL_BRIGHT:light,false);
        VideoMesh.lathe(d,x,y,new double[][]{{r*.86,.009-travel},{r*.79,.011-travel},{r*.58,.0128-travel},{0,.0134-travel}},0xCCD0C8,light,false);
        double z=.014-travel;
        int labelColor=on?0xFFF18C:0xAF572C;
        int labelLight=on?RenderUtil.FULL_BRIGHT:light;
        if(label.equals("◀▶") || label.equals("▶◀") || label.equals("▲") || label.equals("▼")) {
            arrowLegend(d,label,x,y+size*.12,z,size*.40,labelColor,labelLight);
        } else VideoEngraving.text(d,label,x,y+size*.11,z,size*.34,size*.70,labelColor,labelLight);
        if(label.matches("[B0-9F]+")) VideoEngraving.braille(d,label,x,y-size*.20,size,z+.0003,light);
        else if(!label.equals("▲") && !label.equals("▼")) {
            for(int i=0;i<3;i++) VideoMesh.disc(d,x+(i-1)*size*.10,y-size*.22,z,size*.022,0xD6D8D1,light);
        }
    }

    private static void arrowLegend(Draw d,String text,double x,double y,double z,double size,int color,int light) {
        if(text.equals("▲") || text.equals("▼")) {
            double sign=text.equals("▲")?1:-1;
            VideoMesh.triangle(d,x-size*.4,y-sign*size*.20,x,y+sign*size*.25,x+size*.4,y-sign*size*.20,z,color,light);
            return;
        }
        boolean open=text.equals("◀▶");
        for(int side:new int[]{-1,1}) {
            double cx=x+side*size*.28, sign=side*(open?1:-1);
            VideoMesh.triangle(d,cx-sign*size*.15,y-size*.28,cx+sign*size*.15,y,cx-sign*size*.15,y+size*.28,z,color,light);
        }
        VideoMesh.line(d,x,y-size*.3,x,y+size*.3,z,size*.09,color,light);
    }

    /** Timber grip with a circular cross section, polished sleeves and closed round end caps. */
    public static void handrail(Draw d,net.minecraft.world.phys.Vec3 start,net.minecraft.world.phys.Vec3 end,int light) {
        var axis=end.subtract(start).normalize();
        VideoMesh.tube(d,start,end,.027,0xC89D63,light,false);
        for(var tip:new net.minecraft.world.phys.Vec3[]{start,end}) {
            var inward=tip==start?axis:axis.scale(-1);
            VideoMesh.tube(d,tip,tip.add(inward.scale(.065)),.028,0xB9C1C0,light,true);
            // A short curved nose closes the tube; caps are modelled rather than open cylinders.
            d.ps.pushPose();
            d.ps.translate(tip.x,tip.y,tip.z);
            d.ps.mulPose(new org.joml.Quaternionf().rotationTo(0,0,1,(float)-inward.x,(float)-inward.y,(float)-inward.z));
            VideoMesh.lathe(d,0,0,new double[][]{{.028,0},{.026,.007},{.020,.013},{0,.017}},0xC4CAC8,light,true);
            d.ps.popPose();
        }
    }

    public static void intercom(Draw d,double w,double h,double bottom,int light) {
        double y0=Math.max(0,bottom-.28),y1=h-.035;
        // Rounded shield, slightly narrower toward the microphone at its bottom.
        VideoMesh.plate(d,.020,y0,w-.020,y1,w*.14,.003,.005,0xB6BDB8,light,true);
        VideoMesh.plate(d,w*.25,y1-.048,w*.75,y1-.024,.009,.009,.001,0x17201C,light,false);
        VideoMesh.plate(d,w*.17,y0+.13,w*.83,y1-.065,.003,.009,.001,0xE5E3D5,light,false);
        d.textFit("비상시 누름",w/2,y1-.10,.011,.016,w*.60,0x343C35,light);
        for(int i=0;i<7;i++) {
            double x=w*(.27+i*.075),shorten=Math.abs(i-3)*.004;
            VideoMesh.plate(d,x,y0+.050+shorten,x+.007,y0+.112-shorten,.003,.009,.0003,0x242D27,light,false);
        }
        VideoMesh.lathe(d,w/2,y0+.027,new double[][]{{.014,.009},{.012,.012},{.008,.012}},0xA6B0A7,light,true);
        VideoMesh.disc(d,w/2,y0+.027,.0121,.008,0x26352D,light);
        VideoMesh.disc(d,w/2,y0+.027,.0122,.003,0x080E0C,light);
    }

    /** Separate etched specification plate, above the COP rather than a one-line label on it. */
    public static void capacityPlate(Draw d,double cx,double bottom,double width,
                                      com.qwerty.elevator.elevator.ElevatorSettings settings,int floors,int light) {
        double height=width*.66, left=cx-width/2,right=cx+width/2,top=bottom+height;
        VideoMesh.plate(d,left-.003,bottom-.003,right+.003,top+.003,.009,.003,.001,0x8B876E,light,false);
        VideoMesh.plate(d,left,bottom,right,top,.006,.005,.001,0xC6BE9F,light,false);
        // Recessed double border and small fixing heads visible in the reference nameplate.
        double inset=width*.035;
        int ink=0x393B2F;
        VideoMesh.line(d,left+inset,bottom+inset,right-inset,bottom+inset,.0065,.0006,ink,light);
        VideoMesh.line(d,left+inset,top-inset,right-inset,top-inset,.0065,.0006,ink,light);
        VideoMesh.line(d,left+inset,bottom+inset,left+inset,top-inset,.0065,.0006,ink,light);
        VideoMesh.line(d,right-inset,bottom+inset,right-inset,top-inset,.0065,.0006,ink,light);
        String[][] rows={
                {"용도",settings.type.label().getString()},
                {"정원",settings.capacityPersons+" 명"},
                {"적재하중",settings.capacityKg+" kg"},
                {"정격속도",settings.speedMpm+" m/min"},
                {"운행층",floors+" 개층"},
                {"구동방식",settings.drive.label().getString()},
                {"문형식",settings.doorStyle.label().getString()},
                {"호기명",settings.name}
        };
        double rowH=height*.09, start=top-height*.14;
        for(int i=0;i<rows.length;i++) {
            double y=start-i*rowH;
            d.textFit(rows[i][0],left+width*.23,y,.007,width*.032,width*.30,ink,light);
            d.textFit(rows[i][1],left+width*.65,y,.007,width*.032,width*.52,ink,light);
        }
        d.textFit("HYUNDAI ELEVATOR",cx,bottom+height*.075,.007,width*.025,width*.86,ink,light);
        for(double x:new double[]{left+width*.055,right-width*.055}) {
            VideoMesh.disc(d,x,top-height*.07,.007,.0018,0x8A8875,light);
            VideoMesh.line(d,x-.001,top-height*.07,x+.001,top-height*.07,.0072,.0004,ink,light);
        }
    }

    public static void hall(Draw d, DeviceBlockEntity be, ControllerBlockEntity c,
                            boolean up, boolean down, boolean upLit, boolean downLit, int light, float time) {
        double x0=.385,x1=.615;
        d.color(0x687371,light,x0,.08,-.008,x1,1.415,.004);
        VideoMesh.archedPanel(d,.5,(x1-x0)/2,.08,1.44,.018,.004,.018,0xADB5B0,light,true);
        VideoMesh.archedPanel(d,.5,(x1-x0)/2-.014,.54,1.417,.015,.007,.018,0x080C12,light,false);
        d.ps.pushPose();
        d.ps.translate(0,0,.018);
        // Fire prohibition decal, with the lift/person/flame drawing above a red caption strip.
        d.color(0xE8E6DE,light,.443,1.19,.012,.557,1.35,.013);
        int symbol=0x29292A;
        VideoMesh.line(d,.477,1.248,.477,1.307,.014,.003,symbol,light);
        VideoMesh.line(d,.477,1.307,.512,1.307,.014,.003,symbol,light);
        VideoMesh.line(d,.512,1.307,.512,1.248,.014,.003,symbol,light);
        VideoMesh.line(d,.477,1.248,.512,1.248,.014,.003,symbol,light);
        VideoMesh.disc(d,.492,1.291,.014,.005,symbol,light);
        VideoMesh.line(d,.492,1.284,.492,1.267,.014,.005,symbol,light);
        VideoMesh.line(d,.492,1.267,.485,1.254,.014,.004,symbol,light);
        VideoMesh.line(d,.492,1.267,.499,1.254,.014,.004,symbol,light);
        VideoMesh.line(d,.484,1.278,.501,1.278,.014,.004,symbol,light);
        VideoMesh.line(d,.519,1.257,.529,1.271,.014,.003,symbol,light);
        VideoMesh.line(d,.529,1.271,.520,1.281,.014,.003,symbol,light);
        VideoMesh.line(d,.520,1.281,.529,1.292,.014,.003,symbol,light);
        VideoMesh.lathe(d,.5,1.278,new double[][]{{.040,.015},{.033,.015}},0x9F2944,light,false);
        VideoMesh.line(d,.475,1.305,.525,1.25,.016,.007,0x9F2944,light);
        d.color(0x9F2944,light,.443,1.19,.014,.557,1.215,.015);
        d.textFit("화재시 사용금지",.5,1.202,.016,.011,.109,0xFFF4E8,light);
        // Hall unit shows the floor only, rolling in the travel direction on a floor change.
        be.videoDisplay.update(c==null?"--":c.displayName(),c==null?0:c.displayDir(),time);
        if(c==null || c.isMainPower()) {
            double y0=.84,y1=1.03;
            double pitch=Math.min((y1-y0)/8,.16/Math.max(5,be.videoDisplay.current().length()*6-1));
            double x=.5-(be.videoDisplay.current().length()*6-1)*pitch/2;
            double y=(y0+y1)/2-3.5*pitch;
            if(be.videoDisplay.progress(time)<1) {
                double oldX=.5-(be.videoDisplay.previous().length()*6-1)*pitch/2;
                leds(d,be.videoDisplay.previous(),oldX,y+be.videoDisplay.outgoingRows(time)*pitch,pitch,.014,y0,y1,light);
            }
            leds(d,be.videoDisplay.current(),x,y+be.videoDisplay.incomingRows(time)*pitch,pitch,.014,y0,y1,light);
        }
        // Wheelchair pictogram: round wheel, seated figure and straight leg/arm strokes.
        VideoMesh.plate(d,.463,.575,.537,.655,.001,.012,.0001,0xA9B1B4,light,false);
        VideoMesh.plate(d,.465,.577,.535,.653,.001,.013,.0001,0x080C12,light,false);
        VideoMesh.lathe(d,.496,.604,new double[][]{{.022,.014},{.018,.014}},0xC6C9C8,light,false);
        VideoMesh.disc(d,.495,.642,.014,.006,0xC6C9C8,light);
        VideoMesh.line(d,.493,.632,.492,.612,.014,.005,0xC6C9C8,light);
        VideoMesh.line(d,.492,.612,.514,.612,.014,.005,0xC6C9C8,light);
        VideoMesh.line(d,.514,.612,.525,.590,.014,.005,0xC6C9C8,light);
        VideoMesh.line(d,.492,.625,.511,.625,.014,.004,0xC6C9C8,light);
        d.ps.pushPose();
        d.ps.translate(0,0,.012);
        if(up) button(d,.5,down?.405:.32,.083,upLit,false,be.effectiveLight().color,"▲",light);
        if(down) button(d,.5,up?.205:.32,.083,downLit,false,be.effectiveLight().color,"▼",light);
        d.ps.popPose();
        d.ps.popPose();
    }
}
