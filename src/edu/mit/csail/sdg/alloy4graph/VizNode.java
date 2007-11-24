/*
 * Alloy Analyzer
 * Copyright (c) 2007 Massachusetts Institute of Technology
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA,
 * 02110-1301, USA
 */

package edu.mit.csail.sdg.alloy4graph;

import static java.awt.Color.BLACK;
import static java.awt.Color.WHITE;
import java.awt.Color;
import java.awt.Polygon;
import java.awt.Shape;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import static java.lang.StrictMath.sqrt;
import static java.lang.StrictMath.round;

/**
 * Mutable; represents a graphical node.
 *
 * <p><b>Thread Safety:</b> Can be called only by the AWT event thread.
 */

public final strictfp class VizNode extends DiGraph.DiNode {

    // =============================== adjustable options ==================================================

    /** This determines the minimum width of a dummy node. */
    private static final int dummyWidth = 30;

    /** This determines the minimum height of a dummy node. */
    private static final int dummyHeight = 10;

    /** This determines the minimum amount of padding added above, left, right, and below the text label. */
    private static final int labelPadding = 5;

    // =============================== cached for performance efficiency ===================================

    /** Caches the value of sqrt(3.0). The extra digits in the definition will be truncated by the Java compiler. */
    private static final double sqrt3 = 1.7320508075688772935274463415058723669428052538103806280558D;

    /** Caches the value of sin(36 degree). The extra digits in the definition will be truncated by the Java compiler. */
    private static final double sin36 = 0.5877852522924731291687059546390727685976524376431459910723D;

    /** Caches the value of cos(36 degree). The extra digits in the definition will be truncated by the Java compiler. */
    private static final double cos36 = 0.8090169943749474241022934171828190588601545899028814310677D;

    /** Caches the value of cos(18 degree). The extra digits in the definition will be truncated by the Java compiler. */
    private static final double cos18 = 0.9510565162951535721164393333793821434056986341257502224473D;

    /** Caches the value of tan(18 degree). The extra digits in the definition will be truncated by the Java compiler. */
    private static final double tan18 = 0.3249196962329063261558714122151344649549034715214751003078D;

    // =============================== per-node settings ==================================================

    /** a user-provided annotation that will be associated with this node (can be null) */
    public final Object uuid;

    /** The X coordinate of the center of the node. */
    private int centerX = 0;

    /** The Y coordinate of the center of the node. */
    private int centerY = 0;

    /**
     * The font boldness.
     * <p> When this value changes, we automatically invalidate the previously computed bounds information.
     */
    private boolean fontBold = false;

    /**
     * The node labels; if null or empty, then the node has no labels.
     * <p> When this value changes, we automatically invalidate the previously computed bounds information.
     */
    private List<String> labels = null;

    /**
     * The node color; never null.
     * <p> When this value changes, we automatically invalidate the previously computed bounds information.
     */
    private Color color = WHITE;

    /**
     * The line style; never null.
     * <p> When this value changes, we automatically invalidate the previously computed bounds information.
     */
    private VizStyle style = VizStyle.SOLID;

    /**
     * The node shape; if null, then the node is a dummy node.
     * <p> When this value changes, we automatically invalidate the previously computed bounds information.
     */
    private VizShape shape = VizShape.BOX;

    /** Returns the X coordinate of the center of the node. */
    public int x() { return centerX; }

    /** Returns the Y coordinate of the center of the node. */
    public int y() { return centerY; }

    /** Changes the X coordinate of the center of the node. */
    public void setX(int x) { centerX=x;}

    /** Changes the Y coordinate of the center of the node. */
    public void setY(int y) { centerY=y; }

    /** Returns the node shape (or null if the node is a dummy node). */
    public VizShape shape() { return shape; }

    /** Add the given label to the start of the labels, then invalidate the computed bounds. */
    public VizNode addBefore(String label) {
        if (label==null || label.length()==0) return this;
        if (labels==null) labels=new ArrayList<String>();
        labels.add(0,label);
        updown=(-1);
        return this;
    }

    /** Add the given label after the existing labels, then invalidate the computed bounds. */
    public VizNode addAfter(String label) {
        if (label==null || label.length()==0) return this;
        if (labels==null) labels=new ArrayList<String>();
        labels.add(label);
        updown=(-1);
        return this;
    }

    /** Changes the node color, then invalidate the computed bounds. */
    public VizNode set(Color color) {
        if (color!=null && this.color!=color) { this.color=color; updown=(-1); }
        return this;
    }

    /** Changes the node shape (where null means change the node into a dummy node), then invalidate the computed bounds. */
    public VizNode set(VizShape shape) {
        if (this.shape!=shape) { this.shape=shape; updown=(-1); }
        return this;
    }

    /** Changes the line style, then invalidate the computed bounds. */
    public VizNode set(VizStyle style) {
        if (style!=null && this.style!=style) { this.style=style; updown=(-1); }
        return this;
    }

    /** Changes the font boldness, then invalidate the computed bounds. */
    public VizNode setFontBoldness(boolean bold) {
        if (this.fontBold!=bold) { this.fontBold=bold; updown=(-1); }
        return this;
    }

    /** Create a new node with the given list of labels, then add it to the given graph. */
    public VizNode(VizGraph graph, Object uuid, String... labels) {
        super(graph); // The parent's constructor will add this node to the graph automatically
        this.uuid = uuid;
        if (labels==null || labels.length==0) return;
        this.labels = new ArrayList<String>(labels.length);
        for(int i=0; i<labels.length; i++) this.labels.add(labels[i]);
    }

    /** Returns a brief summary of this node. */
    @Override public String toString() { return (labels!=null && labels.size()>0) ? labels.get(0).trim() : ""; }

    // ============================ these are computed by calcBounds() =========================================

    /** If (updown>=0), this is the distance from the center to the top edge. */
    private int updown = (-1);

    /** If (updown>=0), this is the distance from the center to the left edge. */
    private int side = 0;

    /** If (updown>=0), this is the vertical distance between the center of the text label and the center of the node. */
    private int yShift = 0;

    /** If (updown>=0), this is the width of the text label. */
    private int width = 0;

    /** If (updown>=0), this is the height of the text label. */
    private int height = 0;

    /**
     * If (updown>=0 and shape!=null), this is the bounding polygon.
     * Note: if not null, it must be either a GeneralPath or a Polygon.
     */
    private Shape poly = null;

    /**
     * If (updown>=0 and shape!=null and poly2!=null), then poly2 will also be drawn during the draw() method.
     * Note: if not null, it must be either a GeneralPath or a Polygon.
     */
    private Shape poly2 = null;

    /**
     * If (updown>=0 and shape!=null and poly3!=null), then poly3 will also be drawn during the draw() method.
     * Note: if not null, it must be either a GeneralPath or a Polygon.
     */
    private Shape poly3 = null;

    /** Returns the node height. */
    public int getHeight()  { if (updown<0) calcBounds(); return updown+updown; }

    /** Returns the node width. */
    public int getWidth()  { if (updown<0) calcBounds(); return side+side; }

    /** Returns the amount of space we need to reserve on the right hand side for the self edges (0 if this has no self edges now) */
    public int getReserved() {
        int n=selfEdges().size();
        if (n==0) return 0; else return VizEdge.selfLoopMinWidth + VizEdge.selfLoopXGap*(n-1);
    }

    /** Returns true if the given point intersects this node or not. */
    public boolean intersects(double x, double y) {
        if (shape==null) return false;
        if (updown<0) calcBounds();
        return poly.contains(x-centerX, y-centerY);
    }

    /**
     * Find the point of intersection between this node and a given ray, and store the point of intersection into ans.
     * <p> The ray starts from this node's center, and goes through the point (rx,ry) given as arguments.
     * <p> Note: this method may find the wrong point of intersection if the ray is too horizontal.
     */
    public void intersectsNonhorizontalRay(double rx, double ry, Point2D.Double ans) {
       if (shape==null) { ans.x=centerX; ans.y=centerY; return; }
       if (updown<0) calcBounds();
       // Shift the input argument to the center of this node
       rx=rx-centerX; ry=ry-centerY;
       double slope=rx/ry, step=(ry<0 ? -1 : 1);
       // Use the radius to directly compute the intersection, if the shape is CIRCLE, M_CIRCLE, or DOUBLE_CIRCLE
       if (shape==VizShape.CIRCLE || shape==VizShape.M_CIRCLE || shape==VizShape.DOUBLE_CIRCLE) {
          int hw=width/2, hh=height/2;
          int radius = ((int) (sqrt( hw*((double)hw) + ((double)hh)*hh ))) + 2;
          if (shape==VizShape.DOUBLE_CIRCLE) radius=radius+5;
          // x^2+y^2=radius^2  and x=y*slope, thus (1+slope^2)(y^2)=radius^2
          ry=sqrt((radius*radius)/(1+slope*slope)); if (step<0) ry=(-ry);
          ans.x=ry*slope + centerX;
          ans.y=ry + centerY;
          return;
       }
       // Check for intersection
       for(ry=0;;ry=ry+step) {
          rx=ry*slope;
          if (poly.contains(rx, ry)) continue;
          ans.x=rx+centerX; ans.y=ry+centerY; return;
       }
    }

    /**
     * Find the point of intersection between this node and a given ray, and store the point of intersection into ans.
     * <p> The ray starts from this node's center, and goes through the point (rx,ry) given as arguments.
     * <p> Note: this method may find the wrong point of intersection if the ray is too vertical.
     */
    public void intersectsNonverticalRay(double rx, double ry, Point2D.Double ans) {
       if (shape==null) { ans.x=centerX; ans.y=centerY; return; }
       if (updown<0) calcBounds();
       // Shift the input argument to the center of this node
       rx=rx-centerX; ry=ry-centerY;
       double slope=ry/rx, step=(rx<0 ? -1 : 1);
       // Use the radius to directly compute the intersection, if the shape is CIRCLE, M_CIRCLE, or DOUBLE_CIRCLE
       if (shape==VizShape.CIRCLE || shape==VizShape.M_CIRCLE || shape==VizShape.DOUBLE_CIRCLE) {
          int hw=width/2, hh=height/2;
          int radius = ((int) (sqrt( hw*((double)hw) + ((double)hh)*hh ))) + 2;
          if (shape==VizShape.DOUBLE_CIRCLE) radius=radius+5;
          // x^2+y^2=radius^2  and y=x*slope, thus (1+slope^2)(x^2)=radius^2
          rx=sqrt((radius*radius)/(1+slope*slope)); if (step<0) rx=(-rx);
          ans.y=rx*slope + centerY;
          ans.x=rx + centerX;
          return;
       }
       // Check for intersection
       for(rx=0;;rx=rx+step) {
          ry=rx*slope;
          if (poly.contains(rx, ry)) continue;
          ans.x=rx+centerX;
          ans.y=ry+centerY;
          return;
       }
    }

    /** Return the horizontal point of intersection of this node with a horizontal ray at height y going from this.x() rightward. */
    public double intersectsAtHeight(double y) {
       if (shape==null) return 0;
       if (updown<0) calcBounds();
       y=y-centerY;
       if (shape==VizShape.CIRCLE || shape==VizShape.DOUBLE_CIRCLE || shape==VizShape.M_CIRCLE) {
          int hw=width/2, hh=height/2;
          int radius = ((int) (sqrt( hw*((double)hw) + ((double)hh)*hh ))) + 2;
          if (shape==VizShape.DOUBLE_CIRCLE) radius=radius+5;
          return sqrt(radius*radius - y*y) + centerX;
       } else {
          for(double x=0;;x=x+1) if (!poly.contains(x,y)) return x+centerX;
       }
    }

    //===================================================================================================

    /** Calculate this node's bounds. */
    public void calcBounds() {
       yShift=0;
       width=2*labelPadding; if (width<dummyWidth) side=dummyWidth/2;
       height=width;         if (height<dummyHeight) updown=dummyHeight/2;
       poly=(poly2=(poly3=null));
       if (shape==null) return;
       Polygon poly=new Polygon();
       final int ad = Artist.getMaxAscentAndDescent();
       if (labels!=null) for(int i=0; i<labels.size(); i++) {
          String t = labels.get(i);
          Rectangle2D rect = Artist.getStringBounds(fontBold, t);
          int ww = ((int)(rect.getWidth())) + 1; // Round it up
          if (width<ww) width=ww;
          height=height+ad;
       }
       int hw=((width+1)/2)+labelPadding;  if (hw<ad/2) hw=ad/2; width=hw*2; side=hw;
       int hh=((height+1)/2)+labelPadding; if (hh<ad/2) hh=ad/2; height=hh*2; updown=hh;
       switch(shape) {
          case HOUSE:
             yShift = ad/2;
             updown = updown + yShift;
             poly.addPoint(-hw,yShift-hh); poly.addPoint(0,-updown); poly.addPoint(hw,yShift-hh);
             poly.addPoint(hw,yShift+hh); poly.addPoint(-hw,yShift+hh);
             break;
          case INV_HOUSE:
             yShift = -ad/2;
             updown = updown - yShift;
             poly.addPoint(-hw,yShift-hh); poly.addPoint(hw,yShift-hh); poly.addPoint(hw,yShift+hh);
             poly.addPoint(0,updown); poly.addPoint(-hw,yShift+hh);
             break;
          case TRIANGLE:
          case INV_TRIANGLE: {
             int dx = (int) (height/sqrt3); dx=dx+1; if (dx<6) dx=6;
             int dy = (int) (hw*sqrt3);     dy=dy+1; if (dy<6) dy=6; dy=(dy/2)*2;
             side += dx; updown += dy/2;
             if (shape==VizShape.TRIANGLE) {
                yShift = dy/2;
                poly.addPoint(0, -updown); poly.addPoint(hw+dx, updown); poly.addPoint(-hw-dx, updown);
             } else {
                yShift = -dy/2;
                poly.addPoint(0, updown); poly.addPoint(hw+dx, -updown); poly.addPoint(-hw-dx, -updown);
             }
             break;
          }
          case HEXAGON:
             side += ad;
             poly.addPoint(-hw-ad, 0); poly.addPoint(-hw, -hh); poly.addPoint(hw, -hh);
             poly.addPoint(hw+ad, 0); poly.addPoint(hw, hh); poly.addPoint(-hw, hh);
             break;
          case TRAPEZOID:
             side += ad;
             poly.addPoint(-hw,-hh); poly.addPoint(hw,-hh); poly.addPoint(hw+ad,hh); poly.addPoint(-hw-ad,hh);
             break;
          case INV_TRAPEZOID:
             side += ad;
             poly.addPoint(-hw-ad, -hh); poly.addPoint(hw+ad, -hh); poly.addPoint(hw, hh); poly.addPoint(-hw, hh);
             break;
          case PARALLELOGRAM:
             side += ad;
             poly.addPoint(-hw, -hh); poly.addPoint(hw+ad, -hh); poly.addPoint(hw, hh); poly.addPoint(-hw-ad, hh);
             break;
          case M_DIAMOND:
          case DIAMOND:
             if (shape==VizShape.M_DIAMOND) {
                if (hw<10) { hw=10; side=10; width=20; }
                if (hh<10) { hh=10; updown=10; height=20; }
             }
             updown += hw; side += hh;
             poly.addPoint(-hw-hh, 0); poly.addPoint(0, -hh-hw); poly.addPoint(hw+hh, 0); poly.addPoint(0, hh+hw);
             break;
          case M_SQUARE:
             if (hh<hw) hh=hw; else hw=hh;
             if (hh<6) { hh=6; hw=6; }
             this.width=hw*2;  this.side=hw;
             this.height=hh*2; this.updown=hh;
             side += 4; updown +=4;
             poly.addPoint(-hw-4,-hh-4); poly.addPoint(hw+4,-hh-4); poly.addPoint(hw+4,hh+4); poly.addPoint(-hw-4,hh+4);
             break;
          case OCTAGON:
          case DOUBLE_OCTAGON:
          case TRIPLE_OCTAGON: {
             int dx=(width)/3, dy=ad;
             updown += dy;
             poly.addPoint(-hw, -hh); poly.addPoint(-hw+dx, -hh-dy); poly.addPoint(hw-dx, -hh-dy); poly.addPoint(hw, -hh);
             poly.addPoint(hw, hh); poly.addPoint(hw-dx, hh+dy); poly.addPoint(-hw+dx, hh+dy); poly.addPoint(-hw, hh);
             if (shape==VizShape.OCTAGON) break;
             double c=sqrt(dx*dx+dy*dy), a=(dx*dy)/c, k=((a+5)*dy)/dx, r=sqrt((a+5)*(a+5)+k*k)-dy;
             double dx1=((r-5)*dx)/dy, dy1=-(((dx+5D)*dy)/dx-dy-r);
             int x1=(int)(round(dx1)), y1=(int)(round(dy1));
             updown+=5; side+=5;
             poly2=poly; poly=new Polygon();
             poly.addPoint(-hw-5, -hh-y1); poly.addPoint(-hw+dx-x1, -hh-dy-5); poly.addPoint(hw-dx+x1, -hh-dy-5);
             poly.addPoint(hw+5, -hh-y1); poly.addPoint(hw+5, hh+y1); poly.addPoint(hw-dx+x1, hh+dy+5);
             poly.addPoint(-hw+dx-x1, hh+dy+5); poly.addPoint(-hw-5, hh+y1);
             if (shape==VizShape.DOUBLE_OCTAGON) break;
             updown+=5; side+=5;
             poly3=poly; poly=new Polygon(); x1=(int)(round(dx1*2)); y1=(int)(round(dy1*2));
             poly.addPoint(-hw-10, -hh-y1); poly.addPoint(-hw+dx-x1, -hh-dy-10); poly.addPoint(hw-dx+x1, -hh-dy-10);
             poly.addPoint(hw+10, -hh-y1); poly.addPoint(hw+10, hh+y1); poly.addPoint(hw-dx+x1, hh+dy+10);
             poly.addPoint(-hw+dx-x1, hh+dy+10); poly.addPoint(-hw-10, hh+y1);
             break;
          }
          case M_CIRCLE:
          case CIRCLE:
          case DOUBLE_CIRCLE: {
             int radius = ((int) (sqrt( hw*((double)hw) + ((double)hh)*hh ))) + 2;
             if (shape==VizShape.DOUBLE_CIRCLE) radius=radius+5;
             int L = ((int) (radius / cos18))+2, a = (int) (L * sin36), b = (int) (L * cos36), c = (int) (radius * tan18);
             poly.addPoint(-L,0); poly.addPoint(-b,a); poly.addPoint(-c,L); poly.addPoint(c,L); poly.addPoint(b,a);
             poly.addPoint(L,0); poly.addPoint(b,-a); poly.addPoint(c,-L); poly.addPoint(-c,-L); poly.addPoint(-b,-a);
             updown=L; side=L;
             break;
          }
          case EGG:
          case ELLIPSE: {
             int pad = ad/2;
             side+=pad;
             updown+=pad;
             int d = (shape==VizShape.ELLIPSE) ? 0 : (ad/2);
             GeneralPath path=new GeneralPath();
             path.moveTo(-side,d);
             path.quadTo(-side,-updown,0,-updown); path.quadTo(side,-updown,side,d);
             path.quadTo(side,updown,0,updown); path.quadTo(-side,updown,-side,d);
             path.closePath();
             this.poly=path;
             return; // We must return, since otherwise "this.poly" will be overwritten by the local variable "poly"
          }
          default: { // BOX
             if (shape!=VizShape.BOX) { int d=ad/2; hw=hw+d; side=hw; hh=hh+d; updown=hh; }
             poly.addPoint(-hw,-hh); poly.addPoint(hw,-hh); poly.addPoint(hw,hh); poly.addPoint(-hw,hh);
          }
       }
       this.poly=poly;
    }

    /** Assuming calcBounds() have been called, and (x,y) have been set, then this draws the node. */
    public void draw(Artist gr, double scale, boolean highlight) {
       final int top = graph.getTop(), left = graph.getLeft();
       if (shape==null) return;
       if (updown<0) calcBounds();
       gr.set(style, scale);
       gr.translate(centerX-left, centerY-top);
       gr.setFont(fontBold);
       final int ad = Artist.getMaxAscentAndDescent();
       if (highlight) gr.setColor(Color.RED); else gr.setColor(color);
       if (shape==VizShape.CIRCLE || shape==VizShape.M_CIRCLE || shape==VizShape.DOUBLE_CIRCLE) {
          int hw=width/2, hh=height/2;
          int radius = ((int) (sqrt( hw*((double)hw) + ((double)hh)*hh ))) + 2;
          if (shape==VizShape.DOUBLE_CIRCLE) radius=radius+5;
          gr.fillCircle(radius);
          gr.setColor(BLACK);
          gr.drawCircle(radius);
          if (style==VizStyle.DOTTED || style==VizStyle.DASHED) gr.set(VizStyle.SOLID, scale);
          if (shape==VizShape.M_CIRCLE && 10*radius>=25 && radius>5) {
             int d = (int) sqrt((double)(10*radius-25));
             if (d>0) { gr.drawLine(-d,-radius+5,d,-radius+5); gr.drawLine(-d,radius-5,d,radius-5); }
          }
          if (shape==VizShape.DOUBLE_CIRCLE) gr.drawCircle(radius-5);
       } else {
          gr.draw(poly,true);
          gr.setColor(BLACK);
          gr.draw(poly,false);
          if (poly2!=null) gr.draw(poly2,false);
          if (poly3!=null) gr.draw(poly3,false);
          if (style==VizStyle.DOTTED || style==VizStyle.DASHED) gr.set(VizStyle.SOLID, scale);
          if (shape==VizShape.M_DIAMOND) {
             gr.drawLine(-side+8, -8, -side+8, 8); gr.drawLine(-8, -side+8, 8, -side+8);
             gr.drawLine(side-8, -8, side-8, 8); gr.drawLine(-8, side-8, 8, side-8);
          }
          if (shape==VizShape.M_SQUARE) {
             gr.drawLine(-side, -side+8, -side+8, -side); gr.drawLine(side, -side+8, side-8, -side);
             gr.drawLine(-side, side-8, -side+8, side); gr.drawLine(side, side-8, side-8, side);
          }
       }
       gr.set(VizStyle.SOLID, scale);
       int clr = (color.getRGB() & 0xFFFFFF);
       gr.setColor((clr==0x000000 || clr==0xff0000 || clr==0x0000ff) ? Color.WHITE : Color.BLACK);
       if (labels!=null && labels.size()>0) {
          int x=(-width/2), y=yShift+(-labels.size()*ad/2);
          for(int i=0; i<labels.size(); i++) {
             String t = labels.get(i);
             int w = ((int) (Artist.getStringBounds(fontBold, t).getWidth())) + 1; // Round it up
             if (width>w) w=(width-w)/2; else w=0;
             gr.drawString(t, x+w, y+Artist.getMaxAscent());
             y=y+ad;
          }
       }
       gr.translate(left-centerX, top-centerY);
    }

    /** Helper method that sets the Y coordinate of every node in a given layer. */
    private void setY(int layer, int y) {
        for(VizNode n:graph.layer(layer)) n.centerY=y;
    }

    private void shiftUp(int y) {
        final int[] ph = graph.layerPH;
        final int yJump = VizGraph.yJump/6;
        int i=layer();
        setY(i,y);
        y=y-ph[i]/2; // y is now the top-most edge of this layer
        for(i++; i<graph.layers(); i++) {
            List<VizNode> list=graph.layer(i);
            VizNode first=list.get(0);
            if (first.centerY+ph[i]/2+yJump > y) setY(i, y-ph[i]/2-yJump);
            y=first.centerY-ph[i]/2;
        }
        graph.relayout_edges();
    }

    private void shiftDown(int y) {
        final int[] ph = graph.layerPH;
        final int yJump = VizGraph.yJump/6;
        int i=layer();
        setY(i,y);
        y=y+ph[i]/2; // y is now the bottom-most edge of this layer
        for(i--; i>=0; i--) {
            List<VizNode> list=graph.layer(i);
            VizNode first=list.get(0);
            if (first.centerY-ph[i]/2-yJump < y) setY(i, y+ph[i]/2+yJump);
            y=first.centerY+ph[i]/2;
        }
        graph.relayout_edges();
    }

    private void shiftLeft(List<VizNode> peers, int i, int x) {
        final int xJump = VizGraph.xJump/3;
        centerX=x;
        x=x-(shape==null?0:side); // x is now the left-most edge of this node
        for(i--;i>=0;i--) {
            VizNode node=peers.get(i);
            int side=(node.shape==null?0:node.side);
            if (node.centerX+side+node.getReserved()+xJump>x) node.centerX=x-side-node.getReserved()-xJump;
            x=node.centerX-side;
        }
    }

    private void shiftRight(List<VizNode> peers, int i, int x) {
        final int xJump = VizGraph.xJump/3;
        centerX=x;
        x=x+(shape==null?0:side)+getReserved(); // x is now the right most edge of this node
        for(i++;i<peers.size();i++) {
            VizNode node=peers.get(i);
            int side=(node.shape==null?0:node.side);
            if (node.centerX-side-xJump<x) node.centerX=x+side+xJump;
            x=node.centerX+side+node.getReserved();
        }
    }

    private void swapLeft(List<VizNode> peers, int i, int x) {
        int side=(shape==null ? 2 : this.side);
        int left=x-side;
        while(true) {
            if (i==0) { centerX=x; return; } // no clash possible
            VizNode other=peers.get(i-1);
            int otherSide=(other.shape==null ? 2 : other.side);
            int otherRight=other.centerX+otherSide+other.getReserved();
            if (otherRight<left) { centerX=x; return; } // no clash
            graph.swapNodes(layer(), i, i-1);
            i--;
            other.shiftRight(peers, i+1, x + side + getReserved() + otherSide);
        }
    }

    private void swapRight(List<VizNode> peers, int i, int x) {
        int side = (shape==null ? 2 : this.side);
        int right=x+side+getReserved();
        while(true) {
            if (i==peers.size()-1) { centerX=x; return; } // no clash possible
            VizNode other=peers.get(i+1);
            int otherSide=(other.shape==null ? 2 : other.side);
            int otherLeft=other.centerX-otherSide;
            if (otherLeft>right) { centerX=x; return; } // no clash
            graph.swapNodes(layer(), i, i+1);
            i++;
            other.shiftLeft(peers, i-1, x - side - other.getReserved() - otherSide);
        }
    }

    /** Assuming the graph is already laid out, this shifts this node (and re-layouts nearby nodes/edges) */
    public void tweak(int x, int y) {
       if (centerX==x && centerY==y) return; // If no change, then return right away
       List<VizNode> layer = graph.layer(layer());
       final int n = layer.size();
       int i;
       for(i=0; i<n; i++) if (layer.get(i)==this) break; // Figure out this node's position in its layer
       if (centerX>x) swapLeft(layer,i,x); else if (centerX<x) swapRight(layer,i,x);
       //if (centerX>x) shiftLeft(layer,i,x); else if (centerX<x) shiftRight(layer,i,x);
       if (centerY>y) shiftUp(y); else if (centerY<y) shiftDown(y); else graph.relayout_edges(layer());
       graph.recalc_bound(false);
    }
}
