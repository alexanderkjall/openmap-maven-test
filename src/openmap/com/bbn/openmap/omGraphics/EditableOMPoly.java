// **********************************************************************
// 
// <copyright>
// 
//  BBN Technologies, a Verizon Company
//  10 Moulton Street
//  Cambridge, MA 02138
//  (617) 873-8000
// 
//  Copyright (C) BBNT Solutions LLC. All rights reserved.
// 
// </copyright>
// **********************************************************************
// 
// $Source: /cvs/distapps/openmap/src/openmap/com/bbn/openmap/omGraphics/EditableOMPoly.java,v $
// $RCSfile: EditableOMPoly.java,v $
// $Revision: 1.1.1.1 $
// $Date: 2003/02/14 21:35:49 $
// $Author: dietrick $
// 
// **********************************************************************


package com.bbn.openmap.omGraphics;

import com.bbn.openmap.LatLonPoint;
import com.bbn.openmap.layer.util.stateMachine.State;
import com.bbn.openmap.omGraphics.editable.*;
import com.bbn.openmap.proj.Projection;
import com.bbn.openmap.proj.ProjMath;
import com.bbn.openmap.util.Debug;

import java.awt.Component;
import java.awt.Point;
import java.awt.Graphics2D;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import javax.swing.*;

/**
 * The EditableOMPoly encompasses an OMPoly, providing methods for
 * modifying or creating it.
 */
public class EditableOMPoly extends EditableOMGraphic {

    protected ArrayList polyGrabPoints;
    protected OffsetGrabPoint gpo; // offset
    protected OffsetGrabPoint gpm; // for grabbing the poly and moving it.

    protected OMPoly poly;
    /**
     * Whether the poly is a polygon, as opposed to a polyline.  If
     * the poly color is not clear (OMColor.clear) then it will be a
     * polygon.  If it is clear, then it can be set as a polygon -
     * it's otherwise assumed to be a polyline. 
     */
    protected boolean manualEnclosed = false;

    // We'll have to handle this differently...
    public static int OFFSET_POINT_INDEX = -1;

    /**
     * Create the EditableOMPoly, setting the state machine to create
     * the poly off of the gestures.  
     */
    public EditableOMPoly() {
	createGraphic(null);
    }

    /**
     * Create an EditableOMPoly with the polyType and renderType
     * parameters in the GraphicAttributes object.
     */
    public EditableOMPoly(GraphicAttributes ga) {
	createGraphic(ga);
    }

    /**
     * Create the EditableOMPoly with an OMPoly already defined, ready
     * for editing.
     *
     * @param oml OMPoly that should be edited.
     */
    public EditableOMPoly(OMPoly omp) {
	setGraphic(omp);
    }

    /**
     * Create and initialize the state machine that interprets the

     * modifying gestures/commands, as well as ititialize the grab
     * points.  Also allocates the grab point array needed by the
     * EditableOMPoly.
     */
    public void init() {
	Debug.message("eomg", "EditableOMPoly.init()");
	setStateMachine(new PolyStateMachine(this));
	gPoints = new GrabPoint[1];
    }

    /**
     * Set the graphic within the state machine.  If the graphic
     * is null, then one shall be created, and located off screen
     * until the gestures driving the state machine place it on the
     * map.  
     */
    public void setGraphic(OMGraphic graphic) {
	init();
	if (graphic instanceof OMPoly) {
	    poly = (OMPoly)graphic;
	    poly.setDoShapes(true);
	    stateMachine.setSelected();
	    setGrabPoints(poly);
	} else {
	    createGraphic(null);
	}
    }

    /**
     * Method checks if the polygon should be enclosed, and then adds
     * an addition point to the end of the polygon, setting the end
     * point on top of the beginning point.  The two points are
     * OffsetGrabPoints that are tied to each other's position.
     */
    public boolean evaluateEnclosed() {
	deletePoint();
	boolean enclosed = false;

	if (isEnclosed()) {
	    enclose(true);
	    enclosed = true;
	}
	return enclosed;
    }

    /**
     * Method connects the last point to the first point.  Make sure
     * they are both OffsetGrabPoints.  Return true if the points
     * cover the same pixel and if they were successfully joined.
     */
    protected boolean syncEnclosed() {
	try {
	    OffsetGrabPoint gb0 = (OffsetGrabPoint)polyGrabPoints.get(0);
	    OffsetGrabPoint ogb = 
		(OffsetGrabPoint)polyGrabPoints.get(polyGrabPoints.size() - 1);

	    // Check to see if they are over the same point.
	    if (gb0.getX() == ogb.getX() && gb0.getY() == ogb.getY()) {

		// Cross connect them...
		gb0.addGrabPoint(ogb);
		ogb.addGrabPoint(gb0);
		return true;
	    }
	} catch (ClassCastException cce) {
	} catch (IndexOutOfBoundsException ioobe) {
	}
	return false;
    }
    
    /**
     * Method disconnects the last point from the first point.  Make sure
     * they are both OffsetGrabPoints.
     */
    protected boolean unsyncEnclosed() {
	try {
	    OffsetGrabPoint gb0 = (OffsetGrabPoint)polyGrabPoints.get(0);
	    OffsetGrabPoint ogb = 
		(OffsetGrabPoint)polyGrabPoints.get(polyGrabPoints.size() - 1);

	    // disconnect them...
	    if (gb0.getX() == ogb.getX() && gb0.getY() == ogb.getY()) {
		gb0.removeGrabPoint(ogb);
		ogb.removeGrabPoint(gb0);
		return true;
	    }
	} catch (ClassCastException cce) {
	} catch (ArrayIndexOutOfBoundsException aioobe) {
	}
	return false;
    }
    
    public void enclose(boolean e) {
	setEnclosed(e);

	if (polyGrabPoints == null) {
	    return;
	}

	OffsetGrabPoint gb0 = (OffsetGrabPoint)polyGrabPoints.get(0);
	OffsetGrabPoint ogb;
	
	if (e) {
	    // If they should be enclosed...
	    if (!syncEnclosed()) {
		// And they are not already, then add a point, joined
		// to the beginning.
		ogb = new OffsetGrabPoint(gb0.getX(), gb0.getY());
		
		// Add the new point to end of the poly
		addPoint(ogb);
		syncEnclosed();
		repaint();
	    } // Else nothing to do...
	} else {
	    // They shouldn't be hooked up, so check to see if they
	    // are, and disconnect if necessary.
	    if (unsyncEnclosed()) {
		deletePoint(); // Delete attached duplicate point
		repaint();
	    } // else nothing to do.
	}
    }

    /**
     * Set the flag to make the polygon enclosed, which automatically
     * connects the last point with the first point.
     */
    public void setEnclosed(boolean set) {
	manualEnclosed = set;
    }

    /**
     * Returns whether the graphic will be a polygon, instead of a
     * polyline.
     */
    public boolean isEnclosed() {
	return manualEnclosed;
    }

    /**
     * Create and set the graphic within the state machine.  The
     * GraphicAttributes describe the type of poly to create. 
     */
    public void createGraphic(GraphicAttributes ga) {
	init();
	stateMachine.setUndefined();
	int renderType = OMGraphic.RENDERTYPE_LATLON;
	int lineType = OMGraphic.LINETYPE_GREATCIRCLE;

	if (ga != null) {
	    renderType = ga.getRenderType();
	    lineType = ga.getLineType();
	}

	if (Debug.debugging("eomg")) {
	    Debug.output("EditableOMPoly.createGraphic(): rendertype = " +
			 renderType);
	}

	if (lineType == OMGraphic.LINETYPE_UNKNOWN) {
	    lineType = OMGraphic.LINETYPE_GREATCIRCLE;
	    ga.setLineType(OMGraphic.LINETYPE_GREATCIRCLE);
	}

	this.poly = (OMPoly)createGraphic(renderType, lineType);

	if (ga != null) {
	    ga.setRenderType(poly.getRenderType());
	    ga.setTo(poly);
	}
    }

    /**
     *  Extendable method to create specific subclasses of OMPolys.
     */
    public OMGraphic createGraphic(int renderType, int lineType) {
	OMGraphic g = null;
	switch (renderType) {
	case (OMGraphic.RENDERTYPE_LATLON):
	    g = new OMPoly(new float[0], OMGraphic.RADIANS, lineType);
	    break;
	case (OMGraphic.RENDERTYPE_OFFSET):
	    g = new OMPoly(90f, -180f, new int[0], OMPoly.COORDMODE_ORIGIN);
	    break;
	default:
	    g = new OMPoly(new int[0]);
	}
	((OMPoly)g).setDoShapes(true);
	return g;
    }

    /**
     * Get the OMGraphic being created/modified by the EditableOMPoly.  
     */
    public OMGraphic getGraphic() {
	return poly;
    }

    /**
     * Set the GrabPoint that is in the middle of being modified, as a
     * result of a mouseDragged event, or other selection process.
     */
    public void setMovingPoint(GrabPoint gp) {
	super.setMovingPoint(gp);
	gpm = null;
    }

    /**
     * Given a MouseEvent, find a GrabPoint that it is touching, and
     * set the moving point to that GrabPoint.  Called when a
     * MouseEvent happens, and you want to find out if a GrabPoint
     * should be used to make modifications to the graphic or its
     * position.
     *
     * @param e MouseEvent
     * @return GrabPoint that is touched by the MouseEvent, null if
     * none are.
     */
    public GrabPoint getMovingPoint(java.awt.event.MouseEvent e) {
	GrabPoint gb = super.getMovingPoint(e);

	// Since there may be an extra point enclosing the polygon, we
	// want to make sure that the start of the polygon is
	// returned, instead of the duplicate ending point.
	int lastPointIndex = polyGrabPoints.size() - 1;

	if (gb != null && 
	    gb == (GrabPoint)polyGrabPoints.get(lastPointIndex) &&
	    isEnclosed()) {
	    
	    gb = (GrabPoint)polyGrabPoints.get(0);
	    setMovingPoint(gb);
	}
	return gb;
    }

    /**
     * Check to make sure the grab points are not null.  If they are,
     * allocate them, and them assign them to the array. 
     */
    public void assertGrabPoints() {

	// This gets called alot.  Usually, for EditableOMGraphics
	// that have the same number of GrabPoints, they can just be
	// allocated here.  I think we'll have to look at the OMPoly,
	// find out how many points have been defined for it (since
	// it's variable), and make sure everything's OK.

	if (polyGrabPoints == null) {
	    polyGrabPoints = new ArrayList();
	}

	// At least we know about this one.
	if (gpo == null) {
	    gpo = new OffsetGrabPoint(-1, -1);
	}
    }

    /**
     * An internal method that trys to make sure that the grab point
     * for the first node, and for the last, in case of an enclosed
     * polygon, are OffsetGrabPoints.  All of the other points will be
     * regular GrabPoints.  Usually called when assigning points to a
     * previously defined poly.
     *
     * @param x the horizontal pixel location of the grab point.
     * @param y the vertical pixel location of the grab point.
     * @param index the index of the grab point.
     * @param last the index of the last point.  
     */
    protected GrabPoint createGrabPoint(int x, int y, int index, int last) {
	if (index == 0 || 
	    (index == last && (isEnclosed()))) {
	    return new OffsetGrabPoint(x, y);
	} else {
	    return new GrabPoint(x, y);
	}
    }

    /**
     * Set the grab points for the graphic provided, setting them on
     * the extents of the graphic.  Called when you want to set the
     * grab points off the points of the graphic.
     */
    public void setGrabPoints(OMGraphic graphic) {
	if (!(graphic instanceof OMPoly)) {
	    return;
	}

	assertGrabPoints();
	polyGrabPoints.clear();
	gpo.clear();

	OMPoly poly = (OMPoly) graphic;
	boolean ntr = poly.getNeedToRegenerate();
	int renderType = poly.getRenderType();
	Point p = new Point();
	GrabPoint gb;
	int i;
	int npts;


	if (ntr == false) {
	    if (renderType == OMGraphic.RENDERTYPE_LATLON) {
		Debug.message("eomg", "EditableOMPoly: modifying lat/lon line");

		if (projection != null) {
		    
		    float[] ll = poly.getLatLonArray();
		    
		    gb = null; //reset for this loop

		    for (i = 0; i < ll.length; i+=2) {
			projection.forward(ll[i], ll[i+1], p, true);
			// Need to add a grab point for this coordinate
			gb = new OffsetGrabPoint((int)p.getX(), (int)p.getY());
			polyGrabPoints.add(gb);
		    }
		}

	    } else if (renderType == OMGraphic.RENDERTYPE_OFFSET) {
		// Grab the projected endpoints
		Debug.message("eomg", "EditableOMPoly: modifying offset poly");

		int x;
		int y;
		npts = poly.xs.length;


		// Check to see if the poly is a offset poly, and set the
		// offset grab point accordingly.
		if (projection != null) {
		    projection.forward(poly.lat, poly.lon, p, true);
		    gpo.set((int)p.getX(), (int)p.getY());

		    if (poly.coordMode == OMPoly.COORDMODE_ORIGIN) {
			for (i=0; i<npts; i++) {
			    x = poly.xs[i] + p.x;
			    y = poly.ys[i] + p.y;
			    gb = new OffsetGrabPoint(x, y);
			    polyGrabPoints.add(gb);
			}
		    } else { // CMode Previous offset deltas
			int lastX = p.x;
			int lastY = p.y;
			
			for (i=0; i<npts; i++) {
			    x = poly.xs[i] + lastX;
			    y = poly.ys[i] + lastY;

			    gb = new OffsetGrabPoint(x, y);
			    polyGrabPoints.add(gb);

			    lastX += x;
			    lastY += y;
			}
		    }
		}

	    } else {
		npts = poly.xs.length;

		Debug.message("eomg", "EditableOMPoly: modifying x/y poly");
		for (i = 0; i < npts; i++) {
		    gb = new OffsetGrabPoint(poly.xs[i], poly.ys[i]);
		    polyGrabPoints.add(gb);
		}
	    }

	    // Add the || to maintain manualEnclosed if it was
	    // externally set before the OMPoly is actually defined,
	    // indicating that the user wants to draw a polygon.
	    setEnclosed(syncEnclosed() || isEnclosed());
	    addPolyGrabPointsToOGP(gpo);

	} else {
	    Debug.message("eomg", "EditableOMPoly.setGrabPoints: graphic needs to be regenerated ");
	}
    }
    
    /**
     * Take the current location of the GrabPoints, and modify the
     * location parameters of the OMPoly with them.  Called when you
     * want the graphic to change according to the grab points.
     */
    public void setGrabPoints() {
	int i;
	GrabPoint gb; // just to use a temp marker
	LatLonPoint llp = new LatLonPoint();
	int renderType = poly.getRenderType();
	if (renderType == OMGraphic.RENDERTYPE_LATLON) {
	    if (projection != null) {
		float[] floats = new float[polyGrabPoints.size()*2];
		for (i = 0; i < polyGrabPoints.size(); i++) {
		    gb = (GrabPoint) polyGrabPoints.get(i);
		    projection.inverse(gb.getX(), gb.getY(), llp);
		    
		    floats[2*i] = llp.radlat_;
		    floats[2*i+1] = llp.radlon_;
		}

		poly.setLocation((float[])floats, OMGraphic.RADIANS);
	    } else {
		Debug.message("eomg", "EditableOMPoly.setGrabPoints: projection is null, can't figure out LATLON points for poly.");
	    }
	} else if (renderType == OMGraphic.RENDERTYPE_OFFSET) {
	    // Do the offset point.
	    if (projection != null) {

		projection.inverse(gpo.getX(), gpo.getY(), llp);
		
	    } else {
		Debug.message("eomg", "EditableOMPoly.setGrabPoints: projection is null, can't figure out LATLON points for poly offset.");
	    }
	}

	if (renderType == OMGraphic.RENDERTYPE_XY ||
	    renderType == OMGraphic.RENDERTYPE_OFFSET) {

	    int[] ints = new int[polyGrabPoints.size() * 2];
	    if (renderType == OMGraphic.RENDERTYPE_OFFSET && gpo != null) {
		// If offset rendertype, the x-y have to be offset
		// distances, not screen pixel locations.  For the
		// polygon, you also need to take into account that
		// the ints can represent 2 different things: distance
		// from the origin (Offset) or distance from the
		// previous point.  Need to check with the poly to
		// find out which to do.
		GrabPoint previous = gpo;
		
		for (i = 0; i < polyGrabPoints.size(); i++) {
		    gb = (GrabPoint) polyGrabPoints.get(i);

		    if (poly.coordMode == OMPoly.COORDMODE_PREVIOUS) {

			ints[2*i] = gb.getX() - previous.getX();
			ints[2*i+1] = gb.getY() - previous.getY();

			previous = gb;

		    } else {
			ints[2*i] = gb.getX() - gpo.getX();
			ints[2*i+1] = gb.getY() - gpo.getY();
		    }
		}
		
		poly.setLocation(llp.radlat_, llp.radlon_,
				 OMGraphic.RADIANS, ints);


	    } else {

		for (i = 0; i < polyGrabPoints.size(); i++) {
		    gb = (GrabPoint) polyGrabPoints.get(i);

		    ints[2*i] = gb.getX();
		    ints[2*i+1] = gb.getY();
		}

		poly.setLocation(ints);
	    }
	}
	
    }

    /**
     * Add a point to the end of the polyline/polygon and then make it
     * the moving one.
     * @return the index for the point in the polygon, starting with 0.
     */
    public int addMovingPoint(int x, int y) {
	int position = addPoint(x, y);
	setMovingPoint((GrabPoint)polyGrabPoints.get(position));
	return position;
    }

    /**
     * Add a point to the end of the polyline/polygon.
     * @return the index for the point in the polygon, starting with 0.
     */
    public int addPoint(int x, int y) {
	return addPoint(x, y, Integer.MAX_VALUE);
    }

    /**
     * Add a point at a certain point in the polygon coordinate list.
     * If the position is less than zero, the point will be the
     * starting point.  If the position is greater than the list of
     * current points, the point will be added to the end of the poly.
     *
     * @return the index for the point in the polygon, starting with 0.
     */
    public int addPoint(int x, int y, int position) {
//  	if (polyGrabPoints.size() == 0) {
//  	    return addPoint(new OffsetGrabPoint(x, y), position);
//  	}

	return addPoint(new OffsetGrabPoint(x, y), position);
    }

    /**
     * Add a point at a certain point in the polygon coordinate list.
     * If the position is less than zero, the point will be the
     * starting point.  If the position is greater than the list of
     * current points, the point will be added to the end of the poly.
     * This method is convenient because it lets you define the
     * GrabPoint object to use for the node, in case you need a
     * special type of GrabPoint.
     *
     * @param gp the GrabPoint set to the screen coordinates of the
     * point to be added.
     * @return the index for the point in the polygon, starting with 0.  
     */
    public int addPoint(GrabPoint gp) {
	return addPoint(gp, Integer.MAX_VALUE);
    }

    /**
     * Add a point at a certain point in the polygon coordinate list.
     * If the position is less than zero, the point will be the
     * starting point.  If the position is greater than the list of
     * current points, the point will be added to the end of the poly.
     * This method is convenient because it lets you define the
     * GrabPoint object to use for the node, in case you need a
     * special type of GrabPoint.
     *
     * @return the index for the point in the polygon, starting with 0.  
     */
    public int addPoint(GrabPoint gp, int position) {

	if (gp == null) {
	    return -1;
	}

	int x = gp.getX();
	int y = gp.getY();

	int renderType = poly.getRenderType();
	
	if (renderType == OMGraphic.RENDERTYPE_LATLON) {
	    Debug.message("eomg", "EditableOMPoly: adding point to lat/lon poly");
	    
	    if (projection != null) {
		
		float[] ll = poly.getLatLonArray();
		int actualPosition = (position == Integer.MAX_VALUE?ll.length:position*2);
		
		LatLonPoint llpnt = projection.inverse(x, y);

		if (Debug.debugging("eomp")) {
		    Debug.output("EditableOMPoly: adding point to lat/lon poly at " + x + ", " + y + ": " + llpnt + ", at the end of ");

		    for (int j = 0; j < ll.length; j+=2) {
			Debug.output(ll[j] + ", " + ll[j+1]);
		    }
		}

		float[] newll = new float[ll.length + 2];

		if (actualPosition >= ll.length) {
		    // Put the new points at the end
		    if (ll.length != 0) {
			System.arraycopy(ll, 0, newll, 0, ll.length);
		    }
		    newll[ll.length] = llpnt.radlat_;
		    newll[ll.length+1] = llpnt.radlon_;

		    position = ll.length/2;

		} else if (actualPosition <= 0) {
		    // Put the new point at the beginning
		    System.arraycopy(ll, 0, newll, 2, ll.length);
		    newll[0] = llpnt.radlat_;
		    newll[1] = llpnt.radlon_;
		    position = 0;
		} else {
		    // actualPosition because there are 2 floats for every
		    // position.
		    newll[actualPosition] = llpnt.radlat_;
		    newll[actualPosition + 1] = llpnt.radlon_;
		    System.arraycopy(ll, 0, newll, 0, actualPosition);
		    System.arraycopy(ll, actualPosition, newll, actualPosition + 2,
				     ll.length - actualPosition);
		}
		poly.setLocation((float[])newll, OMGraphic.RADIANS);
	    }
	} else if (renderType == OMGraphic.RENDERTYPE_XY) {
	    // Grab the projected endpoints
	    Debug.message("eomg", "EditableOMPoly: adding point to x/y poly");
	    int currentLength = poly.xs.length;
	    int[] newxs = new int[currentLength + 1];
	    int[] newys = new int[currentLength + 1];
	    
	    if (position >= currentLength) {
		// Put the new points at the end
		System.arraycopy(poly.xs, 0, newxs, 0, currentLength);
		System.arraycopy(poly.ys, 0, newys, 0, currentLength);
		newxs[currentLength] = x;
		newys[currentLength] = y;

		position = currentLength;

	    } else if (position <= 0) {
		// Put the new points at the beginning
		System.arraycopy(poly.xs, 0, newxs, 1, currentLength);
		System.arraycopy(poly.ys, 0, newys, 1, currentLength);
		newxs[0] = x;
		newys[0] = y;

		position = 0;

	    } else {
		newxs[position] = x;
		newys[position] = y;
		
		System.arraycopy(poly.xs, 0, newxs, 0, position);
		System.arraycopy(poly.xs, position, newxs, position + 1,
				 currentLength - position);
		
		System.arraycopy(poly.ys, 0, newys, 0, position);
		System.arraycopy(poly.ys, position, newys, position + 1,
				 currentLength - position);
	    }
	    
	    poly.setLocation(newxs, newys);

	} else {
	    // Rendertype is offset...
	    // Grab the projected endpoints
	    Debug.message("eomg", "EditableOMPoly: adding point to offset poly");
	    int currentLength = poly.xs.length;
	    int[] newxs = new int[currentLength + 1];
	    int[] newys = new int[currentLength + 1];

	    if (position >= currentLength) {
		// Put the new points at the end
		position = currentLength;

		System.arraycopy(poly.xs, 0, newxs, 0, currentLength);
		System.arraycopy(poly.ys, 0, newys, 0, currentLength);


	    } else if (position <= 0) {
		// Put the new points at the beginning
		position = 0;

		System.arraycopy(poly.xs, 0, newxs, 1, currentLength);
		System.arraycopy(poly.ys, 0, newys, 1, currentLength);

	    } else {
		
		System.arraycopy(poly.xs, 0, newxs, 0, position);
		System.arraycopy(poly.xs, position, newxs, position + 1,
				 currentLength - position);
		
		System.arraycopy(poly.ys, 0, newys, 0, position);
		System.arraycopy(poly.ys, position, newys, position + 1,
				 currentLength - position);
	    }

	    int offsetX;
	    int offsetY;

	    if (gpo.getX() == -1 && gpo.getY() == -1) {
		offsetX = projection.getWidth()/2;
		offsetY = projection.getHeight()/2;
	    } else {
		offsetX = gpo.getX();
		offsetY = gpo.getY();
	    }

	    if (poly.coordMode == OMPoly.COORDMODE_ORIGIN ||
		position == 0) {  // cover the first point
		
		newxs[position] = x - offsetX;
		newys[position] = y - offsetY;
	    } else { // CMode Previous offset deltas
		newxs[position] = x - offsetX - newxs[position - 1];
		newys[position] = y - offsetY - newys[position - 1];
	    }
	    
	    if (position == 0) {
		// Could call projection.getCenter() but that might
		// break if/when we make other projection
		// libraries/paradigms active.
		LatLonPoint llpnt = projection.inverse(offsetX, offsetY);
		poly.lat = llpnt.radlat_;
		poly.lon = llpnt.radlon_;
	    }
	    
	    poly.setLocation(poly.lat, poly.lon, OMGraphic.RADIANS,
			     newxs, newys);
	}

	poly.regenerate(projection);
	polyGrabPoints.add(position, gp);

	if (gpo != null) {
	    gpo.addGrabPoint(gp);
	}

	return position;
    }

    /**
     * Delete a point off the end of the polyline/polygon.
     */
    public void deletePoint() {
	deletePoint(Integer.MAX_VALUE);
    }

    /**
     * Delete a point at a certain point in the polygon coordinate list.
     * If the position is less than zero, the deleted point will be the
     * starting point.  If the position is greater than the list of
     * current points, the point will be deleted from the end of the poly.
     */
    public void deletePoint(int position) {

	int renderType = poly.getRenderType();


	boolean needToHookUp = false;
	if (position <= 0 && isEnclosed()) {
	    // if the position is 0 and the polygon is enclosed, we
	    // need to disengage the two points, then reattach.
	    enclose(false);
	    needToHookUp = true;
	}
	
	if (renderType == OMGraphic.RENDERTYPE_LATLON) {
	    Debug.message("eomg", "EditableOMPoly: adding point to lat/lon poly");
	    
	    if (projection != null) {
		
		float[] ll = poly.getLatLonArray();
		float[] newll = new float[ll.length - 2];

		int actualPosition = (position == Integer.MAX_VALUE?ll.length:position*2);

		if (actualPosition >= ll.length) {
		    // Pull the new points off the end
		    System.arraycopy(ll, 0, newll, 0, ll.length - 2);
		    position = (ll.length - 2)/2;
		} else if (actualPosition <= 0) {
		    // Pull the new points off the beginning
		    System.arraycopy(ll, 2, newll, 0, ll.length - 2);
		    position = 0;
		} else {
		    // actualPosition because there are 2 floats for every
		    // position.
		    System.arraycopy(ll, 0, newll, 0, actualPosition);
		    System.arraycopy(ll, actualPosition + 2, 
				     newll, actualPosition,
				     ll.length - actualPosition - 2);
		}
		poly.setLocation((float[])newll, OMGraphic.RADIANS);
	    }
	} else {
	    // Grab the projected endpoints
	    Debug.message("eomg", "EditableOMPoly: adding point to x/y or offset poly");
	    int currentLength = poly.xs.length;
	    int[] newxs = new int[currentLength - 1];
	    int[] newys = new int[currentLength - 1];
	    
	    if (position >= currentLength) {
		// Pull the points from the end...
		System.arraycopy(poly.xs, 0, newxs, 0, currentLength - 1);
		System.arraycopy(poly.ys, 0, newys, 0, currentLength - 1);
		position = currentLength - 1;
	    } else if (position <= 0) {
		// Pull the points from the beginning...
		System.arraycopy(poly.xs, 1, newxs, 0, currentLength - 1);
		System.arraycopy(poly.ys, 1, newys, 0, currentLength - 1);
		position = 0;
	    } else {
		
		System.arraycopy(poly.xs, 0, newxs, 0, position);
		System.arraycopy(poly.xs, position + 1, newxs, position,
				 currentLength - position - 1);
		
		System.arraycopy(poly.ys, 0, newys, 0, position);
		System.arraycopy(poly.ys, position + 1, newys, position,
				 currentLength - position - 1);
		
	    }
	    
	    if (poly.getRenderType() == OMGraphic.RENDERTYPE_OFFSET) {
		poly.setLocation(poly.lat, poly.lon, OMGraphic.RADIANS,
				 newxs, newys);
	    } else {
		poly.setLocation(newxs, newys);
	    }
	}
	
	if (projection != null) {
	    poly.regenerate(projection);
	}

	// Remove the GrabPoint for the deleted spot.
	GrabPoint gp = (GrabPoint)polyGrabPoints.remove(position);
	if (gpo != null && gp != null) {
	    gpo.removeGrabPoint(gp);
	}

	if (needToHookUp) {
	    enclose(true);
	}
    }

    /**
     * Called to set the OffsetGrabPoint to the current mouse
     * location, and update the OffsetGrabPoint with all the other
     * GrabPoint locations, so everything can shift smoothly.  Should
     * also set the OffsetGrabPoint to the movingPoint.  Should be
     * called only once at the beginning of the general movement, in
     * order to set the movingPoint.  After that, redraw(e) should
     * just be called, and the movingPoint will make the adjustments
     * to the graphic that are needed.
     */
    public void move(java.awt.event.MouseEvent e) {
	// Need to check to see if the OffsetGrabPoint is currently
	// being used.  If not, just use it, otherwise, will need to
	// create a special one for the move.
	if (poly.getRenderType() == OMGraphic.RENDERTYPE_OFFSET) {
	    gpm = new OffsetGrabPoint(e.getX(), e.getY());
	    gpm.clear();
	} else {
	    gpm = gpo;
	    gpm.clear();
	    gpm.set(e.getX(), e.getY());
	}

	// Move all the other points along with the offset point...
	addPolyGrabPointsToOGP(gpm);

	movingPoint = gpm;
    }

    /**
     * This method adds all the GrabPoints associated with the polygon
     * nodes and adds them to the offset GrabPoint representing the
     * lat/lon anchor point.
     */
    protected void addPolyGrabPointsToOGP(OffsetGrabPoint ogp) {

	if (ogp == null) return;

	// Reset the points to the offset point.
	int count = 0;
	Iterator gps = polyGrabPoints.iterator();
	while (gps.hasNext()) {
	    GrabPoint gb = (GrabPoint) gps.next();
	    ogp.addGrabPoint(gb);
	    count++;
	}

	ogp.updateOffsets();
    }

    /**
     * Use the current projection to place the graphics on the screen.
     * Has to be called to at least assure the graphics that they are
     * ready for rendering.  Called when the graphic position changes.
     *
     * @param proj com.bbn.openmap.proj.Projection
     * @return true 
     */
    public boolean generate(Projection proj) {
	Debug.message("eomg", "EditableOMPoly.generate()");
	if (poly != null) poly.generate(proj);

	// Generate all the grab points
	Iterator gps = polyGrabPoints.iterator();
	while (gps.hasNext()) {
	    GrabPoint gb = (GrabPoint) gps.next();
	    if (gb != null) gb.generate(proj);
	}

	if (gpo != null) {
	    gpo.generate(proj);
	    gpo.updateOffsets();
	};

	return true;
    }

    /**
     * Given a new projection, the grab points may need to be
     * repositioned off the current position of the graphic. Called
     * when the projection changes.
     */
    public void regenerate(Projection proj) {
	Debug.message("eomg", "EditableOMPoly.regenerate()");
	if (poly != null) {
	    poly.generate(proj);
	    setGrabPoints(poly);
	}

	//Generate all the grab points
	Iterator gps = polyGrabPoints.iterator();
	while (gps.hasNext()) {
	    GrabPoint gb = (GrabPoint) gps.next();
	    if (gb != null) {
		gb.generate(proj);
	    }
	}

	if (gpo != null) {
	    gpo.generate(proj);
	    gpo.updateOffsets();
	}
    }

    /**
     * Draw the EditableOMPoly parts into the java.awt.Graphics
     * object.  The grab points are only rendered if the poly machine
     * state is PolySelectedState.POLY_SELECTED.
     *
     * @param graphics java.awt.Graphics.
     */
    public void render(java.awt.Graphics graphics) {
	Debug.message("eomg", "EditableOMPoly.render()");

	State state = getStateMachine().getState();

	if (poly != null && !(state instanceof PolyUndefinedState)) {
	    poly.setVisible(true);
	    poly.render(graphics);
	    poly.setVisible(false);
	} else {
	    Debug.message("eomg", "EditableOMPoly.render: null or undefined poly.");
	    return;
	}
	
	// Render the points actually on the polygon
	if (state instanceof GraphicSelectedState ||
	    state instanceof PolyAddNodeState ||
	    state instanceof PolyDeleteNodeState) {

	    Iterator gps = polyGrabPoints.iterator();
	    while (gps.hasNext()) {
		GrabPoint gb = (GrabPoint) gps.next();
		if (gb != null) {
		    gb.setVisible(true);
		    gb.render(graphics);
		    gb.setVisible(false);
		}
	    }
	}

	// In certain condiditions, render the offset grab point.

	if (state instanceof GraphicSelectedState ||
	    state instanceof GraphicEditState /* ||
	    state instanceof PolySetOffsetState */) {
	    if (gpo != null && 
		poly.getRenderType() == OMGraphic.RENDERTYPE_OFFSET) {
		gpo.setVisible(true);
		gpo.render(graphics);
		gpo.setVisible(false);
	    }
	}
    }

    /////////////// Special Grab Point functions /////////////////////
    // Since the GrabPoints only refer to the points actually on the
    // polygon, we have to make sure that the generic
    // EditableOMGraphic grab point methods handle that.  The
    // OffsetGrabPointIndex is -1, so we have to look out for that and
    // use the gpo when appropriate.
    ///////////////////////////////////////////////////////////////////

    /**
     * Set the grab point objects within the EditableOMGraphic array.
     * For the EditableOMPoly, with its variable number of GrabPoints,
     * this just sets up a new list of all the grab points to look at.
     * It's different than the polyGrabPoints, which are the grab
     * points just on the polygon.  This list includes the offset grab
     * point.  This method should be called when a new point gets
     * added to the polygon, and should take an array of all the
     * GrabPoints created.  It will add the offsetGrabPoint to the end
     * of the array.
     * 
     * @param points a GrabPoint[] for the points on the polygon.
     * @return true if the grab point array was exactly what the
     * EditableOMGraphic was expecting, in terms of length of the
     * GrabPoint array length.  The method copies the array values
     * that fit into the resident array.  
     */
    public boolean setGrabPoints(GrabPoint[] points) {
	gPoints = new GrabPoint[points.length + 1];
	System.arraycopy(gPoints, 0, points, 0, points.length);
	gPoints[points.length] = gpo;

	return true;
    }

    /**
     * Get the array of grab points used for the EditableOMGraphic.
     * Creates the array by copying all the grab points out of the
     * ArrayList, and tacking the offset grab point to the end.
     */
    public GrabPoint[] getGrabPoints() {
	int size = polyGrabPoints.size();

	if (gPoints.length != size + 1) {
	    Debug.message("eomg", "EditableOMPoly.getGrabPoints(): recreating grab points");
	    gPoints = new GrabPoint[size + 1];
	    int counter = 0;
	    Iterator obj = polyGrabPoints.iterator();
	    while (obj.hasNext()) {
		gPoints[counter++] = (GrabPoint)obj.next();
	    }
	    gPoints[counter] = gpo;
	}

	return gPoints;
    }

    /**
     * Set the GrabPoint at a particule index of the array. This can
     * be used to tie two different grab points together.  This used
     * to work with the gPoints array declared in EditableOMGraphic -
     * no longer.  If the index is -1, the offset grab point is set,
     * and any other index refers to the concurrent polygon point.
     *
     * @param gb GrabPoint to assign within array.
     * @param index the index of the array to put the GrabPoint.  This
     * index should be -1 for the offset grab point, or the index of
     * the corner of the poly, in order starting from 0.
     * @return If the grab point or array is null, or if the index is
     * outside the range of the array, false is returned.  If
     * everything goes OK, then true is returned.  
     */
    public boolean setGrabPoint(GrabPoint gb, int index) {
	// We might have to take care of the offset grab point
	// connections here...

	if (index == OFFSET_POINT_INDEX) {
	    gpo = (OffsetGrabPoint) gb;
	    return true;
	} else {
	    return super.setGrabPoint(gb, index);
	}
    }


    /**
     * Given a grab point, what's its index into the polygon array?
     * If it's not in the array, the next available index is returned.
     */
    public int whichGrabPoint(GrabPoint gp) {
	GrabPoint[] points = getGrabPoints();
	for (int i = 0; i < points.length; i++) {
	    if (gp == points[i]) {
		if (gp == gpo) {
		    return OFFSET_POINT_INDEX;
		} else {
		    return i;
		}
	    } 
	}
	return points.length;
    }

    /**
     * Return a particular GrabPoint at a particular point in the
     * array.  The EditableOMGraphic should describe which indexes
     * refer to which grab points in the EOMG GrabPoint array. If the
     * index is OFFSET_POINT_INDEX, the offset point is returned.  If
     * the index is otherwise outside the range of the array, null is
     * returned.  
     */
    public GrabPoint getGrabPoint(int index) {
	if (index == OFFSET_POINT_INDEX) {
	    return gpo;
	} else {
	    return super.getGrabPoint(index);
	}
    }


    /**
     * If this EditableOMGraphic has parameters that can be
     * manipulated that are independent of other EditableOMGraphic
     * types, then you can provide the widgets to control those
     * parameters here.  By default, returns the GraphicAttributes GUI
     * widgets.  If you don't want a GUI to appear when a widget is
     * being created/edited, then don't call this method from the
     * EditableOMGraphic implementation, and return a null Component
     * from getGUI.
     * @param graphicAttributes the GraphicAttributes to use to get
     * the GUI widget from to control those parameters for this EOMG.
     * @return java.awt.Component to use to control parameters for this EOMG.
     */
    public Component getGUI(GraphicAttributes graphicAttributes) {
	Debug.message("eomg", "EditableOMPoly.getGUI");
	if (showGUI) {
	    return buildGUI(graphicAttributes);
	}
	return null;
    }

    JToggleButton polygonButton = null;
    JButton addButton = null;
    JButton deleteButton = null;

    public void enablePolygonButton(boolean enable) {
	if (polygonButton != null) {
	    polygonButton.setEnabled(enable);
	}
    }

    public void enablePolygonEditButtons(boolean enable) {
	if (addButton != null) {
	    addButton.setEnabled(enable);
	}
	if (deleteButton != null) {
	    deleteButton.setEnabled(enable);
	}
    }

    protected java.awt.Component buildGUI(GraphicAttributes graphicAttributes) {

	JPanel palette = new JPanel();
	palette.setLayout(new BoxLayout(palette, BoxLayout.Y_AXIS));
	palette.setAlignmentX(Component.CENTER_ALIGNMENT); // LEFT
	palette.setAlignmentY(Component.CENTER_ALIGNMENT); // BOTTOM

	Box buttonBox = Box.createHorizontalBox();
	
	if (graphicAttributes != null) {
	    palette.add(graphicAttributes.getGUI());
	}

	java.net.URL url = getImageURL("enclosepoly.gif");
	ImageIcon imageIcon = new ImageIcon(url);
	
	polygonButton = new JToggleButton(imageIcon);		
	polygonButton.setSelected(isEnclosed());
	polygonButton.addActionListener(new ActionListener() {
		public void actionPerformed(ActionEvent e) {
		    if (getStateMachine().getState() instanceof GraphicSelectedState) {
			enclose(((JToggleButton) e.getSource()).isSelected());
		    } else {
			setEnclosed(((JToggleButton) e.getSource()).isSelected());
		    }
		}
	    });
	polygonButton.setToolTipText("Automatically link first and last nodes");
	buttonBox.add(polygonButton);

	url = getImageURL("addpoint.gif");
	imageIcon = new ImageIcon(url);
	addButton = new JButton(imageIcon);
	addButton.addActionListener(new ActionListener() {
		public void actionPerformed(ActionEvent e) {
		    ((PolyStateMachine)stateMachine).setAddNode();
		    enablePolygonEditButtons(false);
		}
	    });
	addButton.setEnabled(false);
	addButton.setToolTipText("Add a node to the polygon");
	buttonBox.add(addButton);

	url = getImageURL("deletepoint.gif");
	imageIcon = new ImageIcon(url);
	deleteButton = new JButton(imageIcon);
	deleteButton.addActionListener(new ActionListener() {
		public void actionPerformed(ActionEvent e) {
		    ((PolyStateMachine)stateMachine).setDeleteNode();
		    enablePolygonEditButtons(false);
		}
	    });
	deleteButton.setEnabled(false);
	deleteButton.setToolTipText("Delete a node from the polygon");
	buttonBox.add(deleteButton);

	palette.add(buttonBox);
	
	return palette;
    }

    public java.net.URL getImageURL(String imageName) {
	return this.getClass().getResource(imageName);
    }
}