/* **********************************************************************
 * :vim ts=4 sts=4 sw=4 et ai:
 * Copyright 2000 Intevation GmbH
 * This class is free software under the GNU LGPL
 *
 * useful with OpenMap by BBN Technologies, tested with version 3.6.2. 
 * Add this file to openmap-3.6.2/com/bbn/openmap/layer/vpf/
 *   and add a line to the Makefile
 *
 * 20.12.2000 Bernhard Reiter <bernhard@intevation.de>
 * version=1.0
 * 24.02.2002 Don Dietrick <dietrick@bbn.com>
 * version 1.1 updated to work with OpenMap 4.5, added to OpenMap package.
 *
 * $Id: GenerateVPFProperties.java,v 1.1.1.1 2003/02/14 21:35:49 dietrick Exp $
 * **********************************************************************
 */

package com.bbn.openmap.layer.vpf;

import java.io.File;
import java.util.Vector;
import java.util.List;
import java.util.Enumeration;
import java.util.Hashtable;

import com.bbn.openmap.io.FormatException;
import com.bbn.openmap.util.Debug;

/**
 * This class will print out some basic information about a VPF database,
 * suitable for inclusion into the openmap.properties file.
 * <pre>
 * Usage:
 * java com.bbn.openmap.layer.vpf.GenerateVPFproperties /path/to/vpf/database
 * </pre>
 * It will then print out VPFlayer descriptions which you can use
 * to view the VPF layers with the openmap application, to the standard output.
 * There is no GUI.
 * 
 * If you add the output to the openmap.properties files, pay attention
 * to the Summary: lines. They make it easy to add all the layernames 
 * to the recognised ones.
 * 
 */
public class GenerateVPFProperties extends DescribeDB {
    static String rootpath;
    static LibrarySelectionTable lst;

    /**
     * The main program.  Takes path arguments, and prints the DB it finds
     * @param args the paths to print
     */
    public static void main(String[] args) throws FormatException {
	Debug.init();

	if (args.length == 0) {
	    Debug.output("Usage: java com.bbn.openmap.layer.vpf.GenerateVPFProperties <path to vpf database directory> <path to vpf database directory> ...");
	    System.exit(0);
	}

        for (int argsi = 0; argsi < args.length; argsi++) {
	    rootpath = args[argsi];
	    lst = new LibrarySelectionTable(rootpath);
	    if (Debug.debugging("vpf")) {
		Debug.output("Path to database: " + rootpath);
		Debug.output("Database Name: " + lst.getDatabaseName());
	    }
	    println("### Generated openmap.properties for");
	    println("# VPF Data at: " + rootpath);
	    println("# Description: " + lst.getDatabaseDescription());
	    String[] libraries = lst.getLibraryNames();
	    if (Debug.debugging("vpf")) {
		print("Database Libraries: ");
		for (int i = 0; i < libraries.length; i++) {
		    print(libraries[i], " ");
		}
		println("");
		println("");
	    }
	    for (int i = 0; i < libraries.length; i++) {
		String prefix = lst.getDatabaseName()+"_" + libraries[i];
		println("# Library: " + prefix );
		printLibrary(prefix, lst.getCAT(libraries[i]));
		println("");
	    }
	}
    }
  
    /**
     * Prints a VPF Library
     * @param prefix lines get printed with this prefix
     * @param cat the CoverageAttributeTable (Library) to print
     */
    public static void printLibrary(String prefix, CoverageAttributeTable cat){
	StringBuffer printedlayers = new StringBuffer();
	String printedlayername = null;

        if (cat == null) {
	    System.err.println(prefix + "Library doesn't exist");
	    return;
	}
	String[] coverages = cat.getCoverageNames();
	if (Debug.debugging("vpf")) {
	    Debug.output(prefix + "uses " + (cat.isTiledData() ? "tiled" : "untiled") + " data");
	}
	for (int i = 0; i < coverages.length; i++) {
	    printedlayername=printCoverageProperties(prefix, cat, coverages[i]);
	    if (printedlayername!=null) {
		printedlayers.append(" "+printedlayername);
	    } 
	}
	println("# Summary:"+printedlayers);
    }
  
    /**
     * Prints a VPF Coverage
     * @param prefix this will be the prefix of the generated layer name
     * @param covname the name of the coverage to print
     * @param cat the CoverageAttributeTable to get the Coverage from
     */
    public static String printCoverageProperties(String prefix, CoverageAttributeTable cat, String covname){
        String layername=prefix + "_" + covname;
        Vector text_features=new Vector();
        Vector edge_features=new Vector();
        Vector area_features=new Vector();
        Vector point_features=new Vector();
    
    
        //add topology level
        CoverageTable ct = cat.getCoverageTable(covname);
    
	String path = ct.getDataPath();
	String fcaPath = path + "/fca";

        File fca = new File(fcaPath);
        if (!fca.exists()) {
	    fcaPath = path + "/fca.";
	    fca = new File(fcaPath);
        }
        if (!fca.canRead()) {
            println("");
            return null;
        }
        try {
            DcwRecordFile fcadesc = new DcwRecordFile(fcaPath);
            int fclass = fcadesc.whatColumn("fclass");
            int type = fcadesc.whatColumn("type");
            int descr = fcadesc.whatColumn("descr");
            List v;
            while ((v = fcadesc.parseRow()) != null) {
                String name = (String)v.get(fclass);
                String t = (String)v.get(type);
                String desc = (String)v.get(descr);
                //String tstring = "[unknown] ";
                if (t.equals("T")) {
                    text_features.addElement(name);
                } else if (t.equals("L")) {
                    edge_features.addElement(name);
                } else if (t.equals("A")) {
                    area_features.addElement(name);
                } else if (t.equals("P")) {
                    point_features.addElement(name);
                }
            }
        } catch (FormatException fe) {
            //nevermind, skip it
	}

	// only print something, if we really found features
	if(!(   text_features.isEmpty()&& edge_features.isEmpty()&&
		area_features.isEmpty()&& point_features.isEmpty())) {

	    println("### VPF "+ cat.getCoverageDescription(covname)+" Layer" );
	    println(layername +".class=com.bbn.openmap.layer.vpf.VPFLayer");
	    println(layername + ".prettyName=" 
		    + "VPF "+ cat.getCoverageDescription(covname)+" "+prefix);
	    println(layername + ".vpfPath=" + rootpath);
	    println(layername + ".coverageType=" + covname);
	    println(layername + ".featureTypes=" + "area edge text point");
    
	    printFeatures("text",text_features,layername);
	    printFeatures("edge",edge_features,layername);
	    printFeatures("area",area_features,layername);
	    printFeatures("point",point_features,layername);
	    println("");

	} else {
	    return null;
	}

	return layername;

    }
    /**
     * Print some featureclass names 
     * @param fcis an array of FeatureClassInfo objects whose names get
     * printed
     */
    public static void printFeatures(
        String fname, Vector features, String layername) {
	if(!features.isEmpty()) {
	    print(layername+"."+fname+"=");
	    for (int i = 0; i < features.size(); i++) {
		print(features.elementAt(i)+ " ");
	    }
	    println("");
	}
    }
}