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
// $Source: /cvs/distapps/openmap/src/openmap/com/bbn/openmap/layer/shape/SpatialIndex.java,v $
// $RCSfile: SpatialIndex.java,v $
// $Revision: 1.8 $
// $Date: 2004/10/14 18:06:05 $
// $Author: dietrick $
// 
// **********************************************************************

package com.bbn.openmap.layer.shape;

import java.io.*;
import java.util.Vector;
import javax.swing.ImageIcon;

import com.bbn.openmap.io.BinaryBufferedFile;
import com.bbn.openmap.io.BinaryFile;
import com.bbn.openmap.util.Debug;
import com.bbn.openmap.io.FormatException;

/**
 * A Spatial Index is a variation on a Shape Index, adding the
 * bounding box of the shape to the index.
 * <p>
 * The file has a 100 byte header identical to a Shape Index followed
 * by <i>n </i> records.
 * <p>
 * The record layout of the spatial index is as follows:
 * <p>
 * <TABLE BORDER COLS=5 WIDTH="100%" >
 * <TR>
 * <TD ALIGN=CENTER><b><i>Position </i> </b></TD>
 * <TD ALIGN=CENTER><b><i>Field </i> </b></TD>
 * <TD ALIGN=CENTER><b><i>Value </i> </b></TD>
 * <TD ALIGN=CENTER><b><i>Type </i> </b></TD>
 * <TD ALIGN=CENTER><b><i>Byte Order </i> </b></TD>
 * </TR>
 * <TR>
 * <TD ALIGN=CENTER>Byte 0</TD>
 * <TD ALIGN=CENTER>Offset</TD>
 * <TD ALIGN=CENTER>Offset</TD>
 * <TD ALIGN=CENTER>Integer</TD>
 * <TD ALIGN=CENTER>Big</TD>
 * </TR>
 * <TR>
 * <TD ALIGN=CENTER>Byte 4</TD>
 * <TD ALIGN=CENTER>Content Length</TD>
 * <TD ALIGN=CENTER>Content Length</TD>
 * <TD ALIGN=CENTER>Integer</TD>
 * <TD ALIGN=CENTER>Big</TD>
 * </TR>
 * <TR>
 * <TD ALIGN=CENTER>Byte 8</TD>
 * <TD ALIGN=CENTER>Bounding Box</TD>
 * <TD ALIGN=CENTER>Xmin</TD>
 * <TD ALIGN=CENTER>Double</TD>
 * <TD ALIGN=CENTER>Little</TD>
 * </TR>
 * <TR>
 * <TD ALIGN=CENTER>Byte 16</TD>
 * <TD ALIGN=CENTER>Bounding Box</TD>
 * <TD ALIGN=CENTER>Ymin</TD>
 * <TD ALIGN=CENTER>Double</TD>
 * <TD ALIGN=CENTER>Little</TD>
 * </TR>
 * <TR>
 * <TD ALIGN=CENTER>Byte 24</TD>
 * <TD ALIGN=CENTER>Bounding Box</TD>
 * <TD ALIGN=CENTER>Xmax</TD>
 * <TD ALIGN=CENTER>Double</TD>
 * <TD ALIGN=CENTER>Little</TD>
 * </TR>
 * <TR>
 * <TD ALIGN=CENTER>Byte 32</TD>
 * <TD ALIGN=CENTER>Bounding Box</TD>
 * <TD ALIGN=CENTER>Ymax</TD>
 * <TD ALIGN=CENTER>Double</TD>
 * <TD ALIGN=CENTER>Little</TD>
 * </TR>
 * </TABLE>
 * 
 * <H2>Usage</H2>
 * <DT>java com.bbn.openmap.layer.shape.SpatialIndex -d file.ssx
 * </DT>
 * <DD><i>Dumps spatial index information, excluding bounding boxes
 * to stdout. Useful for comparing to a shape index. </i></DD>
 * <p>
 * <DT>java com.bbn.openmap.layer.shape.SpatialIndex -d -b file.ssx
 * </DT>
 * <DD><i>Dumps spatial index information including bounding boxes
 * to stdout. </i></DD>
 * <p>
 * <DT>java com.bbn.openmap.layer.shape.SpatialIndex -c file.ssx
 * file.shp</DT>
 * <DD><i>Creates spatial index <code>file.ssx</code> from shape
 * file <code>file.shp</code>. </i></DD>
 * <p>
 * 
 * <H2>Notes</H2>
 * When reading the Shape file, the content length is the length of
 * the record's contents, exclusive of the record header (8 bytes). So
 * the size that we need to read in from the Shape file is actually
 * denoted as ((contentLength * 2) + 8). This converts from 16bit
 * units to 8 bit bytes and adds the 8 bytes for the record header.
 * 
 * <H2>To Do</H2>
 * <UL>
 * <LI>index arcs</LI>
 * <LI>index multipoints</LI>
 * </UL>
 * 
 * @author Tom Mitchell <tmitchell@bbn.com>
 * @version $Revision: 1.8 $ $Date: 2004/10/14 18:06:05 $
 * @see ShapeIndex
 */
public class SpatialIndex extends ShapeUtils {

    /** Size of a shape file header in bytes. */
    public final static int SHAPE_FILE_HEADER_LENGTH = 100;

    /** Size of a shape file record header in bytes. */
    public final static int SHAPE_RECORD_HEADER_LENGTH = 8;

    /** Size of the spatial index header in bytes. */
    public final static int SPATIAL_INDEX_HEADER_LENGTH = 100;

    /** Size of the spatial index record in bytes. */
    public final static int SPATIAL_INDEX_RECORD_LENGTH = 40;

    /** Default size for shape record buffer. */
    public final static int DEFAULT_SHAPE_RECORD_SIZE = 50000;

    /** The spatial index file. */
    protected BinaryBufferedFile ssx;

    /** The shape file. */
    protected BinaryBufferedFile shp;

    /** The icon to use for point objects. */
    protected ImageIcon pointIcon;

    /** The bounds of all the shapes in the shape file. */
    protected ESRIBoundingBox bounds = null;

    /**
     * Opens a spatial index file for reading.
     * 
     * @param ssxFilename the name of the spatial index file
     * @exception IOException if something goes wrong opening the file
     */
    public SpatialIndex(String ssxFilename) throws IOException {
        ssx = new BinaryBufferedFile(ssxFilename);
    }

    /**
     * Opens a spatial index file and it's associated shape file.
     * 
     * @param ssxFilename the name of the spatial index file
     * @param shpFilename the name of the shape file
     * @exception IOException if something goes wrong opening the
     *            files
     */
    public SpatialIndex(String ssxFilename, String shpFilename)
            throws IOException {
        if (Debug.debugging("spatialindex")) {
            Debug.output("SpatialIndex(" + ssxFilename + ", " + shpFilename
                    + ");");
        }

        ssx = new BinaryBufferedFile(ssxFilename);
        shp = new BinaryBufferedFile(shpFilename);
    }

    /**
     * Get the box boundary containing all the shapes.
     */
    public ESRIBoundingBox getBounds() {
        if (bounds == null) {
            try {
                locateRecords(-180, -90, 180, 90);
            } catch (IOException ioe) {
                bounds = null;
            } catch (FormatException fe) {
                bounds = null;
            }
        }
        return bounds;
    }

    /**
     * Reset the bounds so they will be recalculated the next time a
     * file is read.
     */
    public void resetBounds() {
        bounds = null;
    }

    /**
     * Creates a record instance from the shape file data. Calls the
     * appropriate record constructor based on the shapeType, and
     * passes the buffer and offset to that constructor.
     * 
     * @param shapeType the shape file's shape type, enumerated in
     *        <code>ShapeUtils</code>
     * @param b the buffer pointing to the raw record data
     * @param off the offset of the data starting point in the buffer
     * @exception IOException if something goes wrong reading the file
     * @see ShapeUtils
     */
    public ESRIRecord makeESRIRecord(int shapeType, byte[] b, int off)
            throws IOException {
        switch (shapeType) {
        case SHAPE_TYPE_NULL:
            return null;
        case SHAPE_TYPE_POINT:
            //          return new ESRIPointRecord(b, off);
            return new ESRIPointRecord(b, off, pointIcon);
        case SHAPE_TYPE_POLYGON:
        case SHAPE_TYPE_ARC:
            //      case SHAPE_TYPE_POLYLINE:
            return new ESRIPolygonRecord(b, off);
        case SHAPE_TYPE_MULTIPOINT:
            Debug.output("SpatialIndex.makeESRIRecord: Arc NYI");
            return null;
        //          return new ESRIMultipointRecord(b, off);
        default:
            return null;
        }
    }

    /**
     * Locates records in the shape file that intersect with the given
     * rectangle. The spatial index is searched for intersections and
     * the appropriate records are read from the shape file.
     * 
     * @param xmin the smaller of the x coordinates
     * @param ymin the smaller of the y coordinates
     * @param xmax the larger of the x coordinates
     * @param ymax the larger of the y coordinates
     * @return an array of records that intersect the given rectangle
     * @exception IOException if something goes wrong reading the
     *            files
     */
    public ESRIRecord[] locateRecords(double xmin, double ymin, double xmax,
                                      double ymax) throws IOException,
            FormatException {

        boolean gatherBounds = false;

        if (bounds == null) {
            bounds = new ESRIBoundingBox();
            gatherBounds = true;
        }

        if (Debug.debugging("spatialindex")) {
            Debug.output("locateRecords:");
            Debug.output("\txmin: " + xmin + "; ymin: " + ymin);
            Debug.output("\txmax: " + xmax + "; ymax: " + ymax);
        }

        byte ixRecord[] = new byte[SPATIAL_INDEX_RECORD_LENGTH];
        int recNum = 0;
        Vector v = new Vector();
        int sRecordSize = DEFAULT_SHAPE_RECORD_SIZE;
        byte sRecord[] = new byte[sRecordSize];

        // Need to figure out what the shape type is...
        ssx.seek(32);

        //      int shapeType = readLEInt(ssx);
        ///
        ssx.byteOrder(false);
        int shapeType = ssx.readInteger();
        ///
        ssx.seek(100); // skip the file header

        while (true) {
            int result = ssx.read(ixRecord, 0, SPATIAL_INDEX_RECORD_LENGTH);
            //          if (result == -1) {
            if (result <= 0) {
                break;//EOF
            } else {
                recNum++;
                double xmin2 = readLEDouble(ixRecord, 8);
                double ymin2 = readLEDouble(ixRecord, 16);
                double xmax2 = readLEDouble(ixRecord, 24);
                double ymax2 = readLEDouble(ixRecord, 32);
                if (Debug.debugging("spatialindexdetail")) {
                    Debug.output("Looking at rec num " + recNum);
                    Debug.output("  " + xmin2 + ", " + ymin2 + "\n  " + xmax2
                            + ", " + ymax2);
                }

                if (gatherBounds) {
                    bounds.addPoint(xmin2, ymin2);
                    bounds.addPoint(xmax2, ymax2);
                }

                if (intersects(xmin,
                        ymin,
                        xmax,
                        ymax,
                        xmin2,
                        ymin2,
                        xmax2,
                        ymax2)) {

                    int offset = readBEInt(ixRecord, 0);
                    int byteOffset = offset * 2;
                    int contentLength = readBEInt(ixRecord, 4);
                    int recordSize = (contentLength * 2) + 8;
                    //                  System.out.print(".");
                    //                  System.out.flush();

                    if (recordSize < 0) {
                        Debug.error("SpatialIndex: supposed to read record size of "
                                + recordSize);
                        break;
                    }

                    if (recordSize > sRecordSize) {
                        sRecordSize = recordSize;
                        if (Debug.debugging("spatialindexdetail")) {
                            Debug.output("Shapefile SpatialIndex record size: "
                                    + sRecordSize);
                        }
                        sRecord = new byte[sRecordSize];
                    }

                    if (Debug.debugging("spatialindex")) {
                        Debug.output("going to shp byteOffset = " + byteOffset
                                + " for record size = " + recordSize
                                + ", offset = " + offset + ", shape type = "
                                + shapeType);
                    }

                    try {
                        shp.seek(byteOffset);
                        int nBytes = shp.read(sRecord, 0, recordSize);
                        if (nBytes < recordSize) {
                            Debug.error("Shapefile SpatialIndex expected "
                                    + recordSize + " bytes, but got " + nBytes
                                    + " bytes instead.");
                        }

                        ESRIRecord record = makeESRIRecord(shapeType,
                                sRecord,
                                0);
                        v.addElement(record);
                    } catch (IOException ioe) {
                        Debug.error("SpatialIndex.locateRecords: IOException. ");
                        ioe.printStackTrace();
                        break;
                    }
                }
            }
        }

        if (Debug.debugging("spatialindex")) {
            Debug.output("Processed " + recNum + " records");
            Debug.output("Selected " + v.size() + " records");
        }
        int nRecords = v.size();

        ssx.seek(0);
        shp.seek(0);
        ESRIRecord result[] = new ESRIRecord[nRecords];
        v.copyInto(result);
        return result;

    }

    /**
     * Determines if two rectangles intersect. Actually, this method
     * determines if two rectangles don't intersect, and then returns
     * a negation of that result. But the bottom line is the same.
     * 
     * @param xmin1 the small x of rectangle 1
     * @param ymin1 the small y of rectangle 1
     * @param xmax1 the big x of rectangle 1
     * @param ymax1 the big y of rectangle 1
     * @param xmin2 the small x of rectangle 2
     * @param ymin2 the small y of rectangle 2
     * @param xmax2 the big x of rectangle 2
     * @param ymax2 the big y of rectangle 2
     * @return <code>true</code> if the rectangles intersect,
     *         <code>false</code> if they do not
     */
    protected static final boolean intersects(double xmin1, double ymin1,
                                              double xmax1, double ymax1,
                                              double xmin2, double ymin2,
                                              double xmax2, double ymax2) {
        return !((xmax1 <= xmin2) || (ymax1 <= ymin2) || (xmin1 >= xmax2) || (ymin1 >= ymax2));
    }

    /**
     * Displays the contents of this index.
     * 
     * @param showBounds true to show bounding box, false to skip it
     * @exception IOException if something goes wrong reading the file
     */
    public void dumpIndex(boolean showBounds) throws IOException {
        byte ixRecord[] = new byte[SPATIAL_INDEX_RECORD_LENGTH];
        int recNum = 0;

        ssx.seek(100); // skip the file header
        while (true) {
            int result = ssx.read(ixRecord, 0, SPATIAL_INDEX_RECORD_LENGTH);
            //          if (result == -1) {
            if (result <= 0) {
                //              Debug.output("Processed " + recNum + " records");
                break;//EOF
            } else {
                recNum++;
                int offset = readBEInt(ixRecord, 0);
                int length = readBEInt(ixRecord, 4);
                Debug.output("Record "
                        + recNum
                        + ": "
                        + offset
                        + ", "
                        + length
                        + (showBounds ? ("; " + readLEDouble(ixRecord, 8)
                                + ", " + readLEDouble(ixRecord, 16) + ", "
                                + readLEDouble(ixRecord, 24) + ", " + readLEDouble(ixRecord,
                                32))
                                : ""));
            }
        }
    }

    /**
     * Writes the spatial index for a polygon shape file.
     * 
     * @param is the shape file input stream
     * @param ptr the current position in the file
     * @param os the spatial index file output stream
     */
    protected static void indexPolygons(InputStream is, long ptr,
                                        OutputStream os) {
        boolean moreRecords = true;
        byte rHdr[] = new byte[SHAPE_RECORD_HEADER_LENGTH];
        byte outBuf[] = new byte[SPATIAL_INDEX_RECORD_LENGTH];
        int result;
        int nRecords = 0;
        int recLengthWords, recLengthBytes, recNumber;
        long recOffset;
        int recBufSize = 100000;
        byte recBuf[] = new byte[recBufSize];
        ESRIBoundingBox polyBounds;

        try {
            while (moreRecords) {
                result = is.read(rHdr, 0, SHAPE_RECORD_HEADER_LENGTH);
                if (result < 0) {
                    moreRecords = false;
                    Debug.output("Shapefile SpatialIndex Found " + nRecords
                            + " records");
                    Debug.output("Shapefile SpatialIndex recBufSize = "
                            + recBufSize);
                } else {
                    nRecords++;
                    recOffset = ptr;
                    recNumber = readBEInt(rHdr, 0);
                    recLengthWords = readBEInt(rHdr, 4);
                    recLengthBytes = recLengthWords * 2;

                    if (recLengthBytes > recBufSize) {
                        Debug.output("Shapefile SpatialIndex increasing recBufSize to "
                                + recLengthBytes);
                        recBufSize = recLengthBytes;
                        recBuf = new byte[recBufSize];
                    }

                    result = is.read(recBuf, 0, recLengthBytes);
                    polyBounds = readBox(recBuf, 4);
                    ptr += recLengthBytes + 8;

                    writeBEInt(outBuf, 0, (int) (recOffset / 2));
                    writeBEInt(outBuf, 4, recLengthWords);
                    writeLEDouble(outBuf, 8, polyBounds.min.x);
                    writeLEDouble(outBuf, 16, polyBounds.min.y);
                    writeLEDouble(outBuf, 24, polyBounds.max.x);
                    writeLEDouble(outBuf, 32, polyBounds.max.y);
                    os.write(outBuf, 0, SPATIAL_INDEX_RECORD_LENGTH);
                }
            }
        } catch (java.io.IOException e) {
            e.printStackTrace();
        } finally {
            try {
                is.close();
            } catch (java.io.IOException e) {
            }
        }
    }

    /**
     * Writes the spatial index for a point shape file.
     * 
     * @param is the shape file input stream
     * @param ptr the current position in the file
     * @param os the spatial index file output stream
     */
    protected static void indexPoints(InputStream is, long ptr, OutputStream os) {
        boolean moreRecords = true;
        byte rHdr[] = new byte[SHAPE_RECORD_HEADER_LENGTH];
        byte outBuf[] = new byte[SPATIAL_INDEX_RECORD_LENGTH];
        int result;
        int nRecords = 0;
        int recLengthWords, recLengthBytes, recNumber;
        long recOffset;
        int recBufSize = 20;
        byte recBuf[] = new byte[recBufSize];
        double x;
        double y;

        try {
            while (moreRecords) {
                result = is.read(rHdr, 0, SHAPE_RECORD_HEADER_LENGTH);
                if (result < 0) {
                    moreRecords = false;
                    Debug.output("Found " + nRecords + " records");
                    Debug.output("recBufSize = " + recBufSize);
                } else {
                    nRecords++;
                    recOffset = ptr;
                    recNumber = readBEInt(rHdr, 0);
                    recLengthWords = readBEInt(rHdr, 4);
                    recLengthBytes = recLengthWords * 2;

                    if (recLengthBytes > recBufSize) {
                        Debug.output("Shapefile SpatialIndex increasing recBufSize to "
                                + recLengthBytes);
                        recBufSize = recLengthBytes;
                        recBuf = new byte[recBufSize];
                    }

                    result = is.read(recBuf, 0, recLengthBytes);
                    x = readLEDouble(recBuf, 4);
                    y = readLEDouble(recBuf, 12);
                    ptr += recLengthBytes + 8;

                    writeBEInt(outBuf, 0, (int) (recOffset / 2));
                    writeBEInt(outBuf, 4, recLengthWords);
                    writeLEDouble(outBuf, 8, x);
                    writeLEDouble(outBuf, 16, y);
                    writeLEDouble(outBuf, 24, x);
                    writeLEDouble(outBuf, 32, y);
                    os.write(outBuf, 0, SPATIAL_INDEX_RECORD_LENGTH);
                }
            }
        } catch (java.io.IOException e) {
            e.printStackTrace();
        } finally {
            try {
                is.close();
            } catch (java.io.IOException e) {
            }
        }
    }

    /**
     * Creates a spatial index for a shape file. Reads the records
     * from the shape file, writing appropriate index records to the
     * spatial index file.
     * 
     * @param inFile the shape file
     * @param outFile the spatial index file
     */
    public static void createIndex(String inFile, String outFile) {
        byte fileHeader[] = new byte[SHAPE_FILE_HEADER_LENGTH];
        FileInputStream shp = null;
        FileOutputStream ssx = null;
        int shapeType;
        try {
            shp = new FileInputStream(inFile);
            ssx = new FileOutputStream(outFile);
            shp.read(fileHeader, 0, SHAPE_FILE_HEADER_LENGTH);
            ssx.write(fileHeader, 0, SHAPE_FILE_HEADER_LENGTH);
            shapeType = readLEInt(fileHeader, 32);
            switch (shapeType) {
            case SHAPE_TYPE_NULL:
                Debug.error("Unable to index shape type NULL");
                break;
            case SHAPE_TYPE_POINT:
                indexPoints(shp, SHAPE_FILE_HEADER_LENGTH, ssx);
                break;
            case SHAPE_TYPE_ARC:
                //          case SHAPE_TYPE_POLYLINE:
                indexPolygons(shp, SHAPE_FILE_HEADER_LENGTH, ssx);
                break;
            case SHAPE_TYPE_POLYGON:
                indexPolygons(shp, SHAPE_FILE_HEADER_LENGTH, ssx);
                break;
            case SHAPE_TYPE_MULTIPOINT:
                Debug.error("Shapefile SpatialIndex: Unable to index shape type MULTIPOINT");
                break;
            default:
                Debug.error("Shapefile SpatialIndex.createIndex:  Unknown shape type: "
                        + shapeType);
            }

        } catch (java.io.IOException e) {
            e.printStackTrace();
        } finally {
            try {
                shp.close();
                ssx.close();
            } catch (java.io.IOException e) {
            }
        }
    }

    /**
     * Prints a usage statement describing how to use this class from
     * the command line.
     * 
     * @param out The output stream to use for output
     */
    public static void printUsage(PrintStream out) {
        String className = SpatialIndex.class.getName();

        out.println("Usage:");
        out.println();
        out.println("java " + className + " -c file.ssx file.shp");
        out.println("Creates spatial index <file.ssx> from "
                + "shape file <file.shp>.");
        out.println();
        out.println("java " + className + " -d file.ssx");
        out.println("Dumps spatial index information, excluding "
                + "bounding boxes to stdout.  Useful for "
                + "comparing to a shape index.");
        out.println();
        out.println("java " + className + " -d -b file.ssx");
        out.println("Dumps spatial index information including "
                + "bounding boxes to stdout.");
        out.println();
    }

    /**
     * Locate file 'fileName' in classpath, if it is not an absolute
     * file name.
     * 
     * @return absolute name of the file as a string if found, null
     *         otherwise.
     */
    public static String locateFile(String name) {
        File file = new File(name);
        if (file.exists()) {
            return name;
        } else {
            java.net.URL url = ClassLoader.getSystemResource(name);

            //OK, now we want to look around for the file, in the
            //classpaths, and as a resource. It may be a file in
            //a classpath, available for direct access.
            if (url != null) {
                String newname = url.getFile();
                file = new File(newname);
                if (file.exists()) {
                    return newname;
                }
            }
        }
        return null;
    }

    /**
     * Create a SpatialIndex object with just a shape file name. If
     * the shape file is local, this method will attempt to build the
     * spatial index file and place it next to the shape file.
     */
    public static SpatialIndex locateAndSetShapeData(String shapeFileName) {
        SpatialIndex spi = null;
        int appendixIndex = shapeFileName.indexOf(".shp");
        String spatialIndexFileName, newShapeFileName, newSpatialIndexFileName;

        if (Debug.debugging("shape")) {
            Debug.output("SpatialIndex: created with just the shape file "
                    + shapeFileName);
        }

        if (appendixIndex != -1) {

            if (BinaryFile.exists(shapeFileName)) {
                // OK, the shape files exists - now look for spatial
                // index file next to it.
                spatialIndexFileName = shapeFileName.substring(0, appendixIndex)
                        + ".ssx";

                // Now, see if the spatialIndexFileName exists, and if
                // not, create it.
                if (Debug.debugging("shape")) {
                    Debug.output("Trying to locate spatial index file "
                            + spatialIndexFileName);
                }

                if (!BinaryFile.exists(spatialIndexFileName)) {
                    // OK, the spatial index doesn't exist, but if the
                    // shape file is local, we have a shot at creating
                    // it.
                    newShapeFileName = locateFile(shapeFileName);

                    if (newShapeFileName != null) {
                        // It's Local!!
                        Debug.output("Creating spatial index file: "
                                + spatialIndexFileName);

                        appendixIndex = newShapeFileName.indexOf(".shp");
                        newSpatialIndexFileName = newShapeFileName.substring(0,
                                appendixIndex)
                                + ".ssx";
                        SpatialIndex.createIndex(newShapeFileName,
                                newSpatialIndexFileName);
                    } else {
                        Debug.error("Can't create SpatialIndex for URL/JAR shapefile: "
                                + shapeFileName);
                    }
                }

                try {
                    spi = new SpatialIndex(spatialIndexFileName, shapeFileName);
                } catch (java.io.IOException ioe) {
                    Debug.error(ioe.getMessage());
                    ioe.printStackTrace(Debug.getErrorStream());
                    spi = null;
                }
            } else {
                Debug.error("SpatialIndex: Couldn't locate shape file "
                        + shapeFileName);
            }

        } else {
            if (Debug.debugging("shape")) {
                Debug.output("SpatialIndex: file " + shapeFileName
                        + " doesn't look like a shape file");
            }
        }

        return spi;
    }

    public static SpatialIndex locateAndSetShapeData(String shapeFileName,
                                                     String spatialIndexFileName) {
        SpatialIndex spi = null;
        String message = "ShapeLayer SpatialIndex: problem setting up the shape files:\n      shape file: "
                + shapeFileName
                + "\n     spatial index file: "
                + spatialIndexFileName;

        try {
            if (BinaryFile.exists(shapeFileName)
                    && BinaryFile.exists(spatialIndexFileName)) {
                spi = new SpatialIndex(spatialIndexFileName, shapeFileName);
            } else {
                Debug.error(message);
            }
        } catch (java.io.IOException ioe) {
            Debug.error(message + "\n" + ioe.getMessage());
            ioe.printStackTrace(Debug.getErrorStream());
        }
        return spi;
    }

    /**
     * The driver for the command line interface. Reads the command
     * line arguments and executes appropriate calls.
     * <p>
     * See the file documentation for usage.
     * 
     * @param argv the command line arguments
     * @exception IOException if something goes wrong reading or
     *            writing the file
     */
    public static void main(String argv[]) throws IOException {
        int argc = argv.length;

        if (argc == 0) {
            // No arguments, give the user some help
            printUsage(System.out);
            System.exit(0);
        }

        if (argv[0].equals("-d")) {
            if (argc == 2) {
                String name = argv[1];
                SpatialIndex si = new SpatialIndex(name);
                si.dumpIndex(false);
            } else if ((argc == 3) && (argv[1].equals("-b"))) {
                String name = argv[2];
                SpatialIndex si = new SpatialIndex(name);
                si.dumpIndex(true);
            } else {
                printUsage(System.err);
                System.exit(1);
            }
        } else if ((argc == 3) && argv[0].equals("-c")) {
            String indexFile = argv[1];
            String shapeFile = argv[2];
            SpatialIndex.createIndex(shapeFile, indexFile);
        } else {
            printUsage(System.err);
            System.exit(1);
        }
    }

    /**
     * Set the icon to use for point objects, in general.
     * 
     * @param ii ImageIcon to use for icon.
     */
    public synchronized void setPointIcon(ImageIcon ii) {
        pointIcon = ii;
    }

    /**
     * Get the icon used for general point objects.
     * 
     * @return ImageIcon, null if not set.
     */
    public synchronized ImageIcon getPointIcon() {
        return pointIcon;
    }
}

