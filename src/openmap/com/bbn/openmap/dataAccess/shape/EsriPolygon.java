// **********************************************************************
// 
// <copyright>
// 
//  BBN Technologies
//  10 Moulton Street
//  Cambridge, MA 02138
//  (617) 873-8000
// 
//  Copyright (C) BBNT Solutions LLC. All rights reserved.
// 
// </copyright>
// **********************************************************************
// 
// $Source: /cvs/distapps/openmap/src/openmap/com/bbn/openmap/dataAccess/shape/EsriPolygon.java,v $
// $RCSfile: EsriPolygon.java,v $
// $Revision: 1.7 $
// $Date: 2004/10/14 18:05:43 $
// $Author: dietrick $
// 
// **********************************************************************

package com.bbn.openmap.dataAccess.shape;

import com.bbn.openmap.omGraphics.*;
import com.bbn.openmap.proj.ProjMath;

/**
 * An extension to OMPoly for polygons that typecasts a specific Esri
 * graphic type. Used to ensure that all OMGraphics added to a
 * EsriGraphicList is of the same type.
 * 
 * @author Doug Van Auken
 */
public class EsriPolygon extends OMPoly implements Cloneable, EsriGraphic {

    protected float[] extents;

    public EsriPolygon(float[] points, int units, int lineType) {
        super(points, units, lineType);
        float[] degreePoints = new float[points.length];
        System.arraycopy(points, 0, degreePoints, 0, points.length);
        addExtents(ProjMath.arrayRadToDeg(degreePoints));
    }

    /**
     * The lat/lon extent of the EsriGraphic, assumed to contain miny,
     * minx, maxy maxx in order of the array.
     */
    public void setExtents(float[] extents) {
        this.extents = extents;
    }

    /**
     * The lat/lon extent of the EsriGraphic, returned as miny, minx,
     * maxy maxx in order of the array.
     */
    public float[] getExtents() {
        if (extents == null) {
            // These are set to their opposites to guarantee some
            // movement.
            extents = new float[] { 90f, 180f, -90f, -180f };
        }
        return extents;
    }

    public void addExtents(float[] graphicExtents) {
        float[] ex = getExtents();

        // Check both graphic extents in case they are inadvertently
        // switched.
        for (int i = 0; i < graphicExtents.length; i += 2) {
            if (ex[0] > graphicExtents[i])
                ex[0] = graphicExtents[i];
            if (ex[1] > graphicExtents[i + 1])
                ex[1] = graphicExtents[i + 1];
            if (ex[2] < graphicExtents[i])
                ex[2] = graphicExtents[i];
            if (ex[3] < graphicExtents[i + 1])
                ex[3] = graphicExtents[i + 1];
        }

        //      System.out.println("extents of Polygon: " +
        //                         ex[1] + ", " +
        //                         ex[0] + ", " +
        //                         ex[3] + ", " +
        //                         ex[2]);
    }

    public static EsriPolygon convert(OMPoly ompoly) {
        if (ompoly.getRenderType() == RENDERTYPE_LATLON) {

            float[] rawLL = ompoly.getLatLonArray();
            float[] degreePoints = new float[rawLL.length];
            System.arraycopy(rawLL, 0, degreePoints, 0, rawLL.length);

            EsriPolygon ePoly = new EsriPolygon(degreePoints, OMGraphic.RADIANS, ompoly.getLineType());
            DrawingAttributes attributes = new DrawingAttributes();
            attributes.setFrom(ompoly);
            attributes.setTo(ePoly);
            ePoly.setAppObject(ompoly.getAppObject());
            return ePoly;
        } else {
            return null;
        }
    }

    public EsriGraphic shallowCopy() {
        return shallowCopyPolygon();
    }

    public EsriPolygon shallowCopyPolygon() {
        return (EsriPolygon) clone();
    }
}