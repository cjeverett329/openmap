// **********************************************************************
//
// <copyright>
//
// BBN Technologies, a Verizon Company
// 10 Moulton Street
// Cambridge, MA 02138
// (617) 873-8000
//
// Copyright (C) BBNT Solutions LLC. All rights reserved.
//
// </copyright>

package com.bbn.openmap.gui.time;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Polygon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JPanel;

import com.bbn.openmap.Environment;
import com.bbn.openmap.I18n;
import com.bbn.openmap.InformationDelegator;
import com.bbn.openmap.MapBean;
import com.bbn.openmap.MapHandler;
import com.bbn.openmap.event.CenterListener;
import com.bbn.openmap.event.CenterSupport;
import com.bbn.openmap.event.MapMouseListener;
import com.bbn.openmap.gui.time.TimeSliderLayer.TimeDrape;
import com.bbn.openmap.gui.time.TimelineLayer.SelectionArea.PlayFilterSection;
import com.bbn.openmap.layer.OMGraphicHandlerLayer;
import com.bbn.openmap.omGraphics.DrawingAttributes;
import com.bbn.openmap.omGraphics.OMAction;
import com.bbn.openmap.omGraphics.OMGraphic;
import com.bbn.openmap.omGraphics.OMGraphicList;
import com.bbn.openmap.omGraphics.OMLine;
import com.bbn.openmap.omGraphics.OMRaster;
import com.bbn.openmap.omGraphics.OMRect;
import com.bbn.openmap.omGraphics.OMText;
import com.bbn.openmap.proj.Cartesian;
import com.bbn.openmap.proj.Projection;
import com.bbn.openmap.time.Clock;
import com.bbn.openmap.time.TimeBounds;
import com.bbn.openmap.time.TimeEvent;
import com.bbn.openmap.tools.drawing.DrawingToolRequestor;
import com.bbn.openmap.tools.icon.BasicIconPart;
import com.bbn.openmap.tools.icon.IconPart;
import com.bbn.openmap.tools.icon.OMIconFactory;

/**
 * Timeline layer
 * 
 * Render events and allow for their selection on a variable-scale timeline
 */
public class TimelineLayer extends OMGraphicHandlerLayer implements
        ActionListener, DrawingToolRequestor, PropertyChangeListener,
        MapMouseListener, ComponentListener {
    /**
     * This property is used to signify whether any AAREvents have been
     * designated as play filterable, so GUI controls for the play filter can be
     * enabled/disabled.
     */
    public final static String PlayFilterProperty = "playfilter";
    /**
     * This property is used to send the current offset time where the mouse is
     * over the timeline.
     */
    public final static String MouseTimeProperty = "mouseTime";
    /**
     * This property is used to send event details that can be displayed when
     * the mouse is over an event in the timeline.
     */
    public final static String EventDetailsProperty = "eventDetails";
    /**
     * This property is used to notify listeners that the time projection
     * parameters have changed, and they need to contact this object to figure
     * out how to display those changes.
     */
    public final static String TimeParametersProperty = "timeParameters";
    public static Logger logger = Logger.getLogger("com.bbn.hotwash.gui.TimelineLayer");

    protected I18n i18n = Environment.getI18n();

    protected OMGraphicList eventGraphicList = null;
    protected OMGraphicList timeLinesList = null;
    protected PlayFilter playFilter = new PlayFilter();
    protected OMGraphicList ratingAreas = new OMGraphicList();
    protected SelectionArea selectionRect;
    protected TimeSliderLayer.TimeDrape drape;
    protected CenterSupport centerDelegate;

    long currentTime = 0;
    long gameStartTime = 0;
    long gameEndTime = 0;

//    protected EventListPresenter eventListPresenter;
//    protected AAREventSelectionCoordinator aesc;
    protected static Color tint = new Color(0x99000000, true);

    protected Clock clock;

    /**
     * Construct the TimelineLayer.
     */
    public TimelineLayer() {
        setName("Timeline");

        // This is how to set the ProjectionChangePolicy, which
        // dictates how the layer behaves when a new projection is
        // received.
        setProjectionChangePolicy(new com.bbn.openmap.layer.policy.StandardPCPolicy(this, false));
        // Making the setting so this layer receives events from the
        // SelectMouseMode, which has a modeID of "Gestures". Other
        // IDs can be added as needed.
        setMouseModeIDsForEvents(new String[] { "Gestures" });

        centerDelegate = new CenterSupport(this);
        addComponentListener(this);
        
        drape = new TimeDrape(0, 0, -1, -1);
        drape.setFillPaint(Color.gray);
        drape.setVisible(true);

    }

    public void findAndInit(Object someObj) {
        if (someObj instanceof Clock) {
            clock = (Clock) someObj;
            clock.addPropertyChangeListener(Clock.TIMER_STATUS_PROPERTY, this);
            clock.addPropertyChangeListener(Clock.TIME_BOUNDS_PROPERTY, this);
            setTimeBounds(clock.getStartTime(), clock.getEndTime());
        }
        if (someObj instanceof CenterListener) {
            centerDelegate.addCenterListener((CenterListener) someObj);
        }
//        if (someObj instanceof EventListPresenter) {
//            eventListPresenter = (EventListPresenter) someObj;
//            selectionRect = null;
//            eventListPresenter.addPropertyChangeListener(this);
//        }
//        if (someObj instanceof InformationDelegator) {
//            ((InformationDelegator) someObj).setPreferredLocation(java.awt.BorderLayout.NORTH);
//        }
//
//        if (someObj instanceof AAREventSelectionCoordinator) {
//            aesc = (AAREventSelectionCoordinator) someObj;
//            aesc.addPropertyChangeListener(this);
//        }
        if (someObj instanceof TimePanel.Wrapper) {
            TimePanel tp = ((TimePanel.Wrapper) someObj).getTimePanel();
            tp.addPropertyChangeListener(this);
            addPropertyChangeListener(tp);
        }
    }

    public void findAndUndo(Object someObj) {
        if (someObj == clock) {
            ((Clock) someObj).removePropertyChangeListener(Clock.TIMER_STATUS_PROPERTY,
                    this);
        }
        if (someObj instanceof CenterListener) {
            centerDelegate.removeCenterListener((CenterListener) someObj);
        }
//        if (someObj == eventListPresenter) {
//            eventListPresenter.removePropertyChangeListener(this);
//            eventListPresenter = null;
//        }
//        if (someObj == aesc) {
//            aesc.removePropertyChangeListener(this);
//            aesc = null;
//        }
        if (someObj instanceof TimePanel.Wrapper) {
            TimePanel tp = ((TimePanel.Wrapper) someObj).getTimePanel();
            removePropertyChangeListener(tp);
            tp.removePropertyChangeListener(this);
        }
    }

    public static double forwardProjectMillis(long time) {
        return (double) time / 60000f; // 60000 millis per minute
    }

    public static long inverseProjectMillis(double timef) {
        return (long) (timef * 60000f); // 60000 millis per minute
    }

    // public void setProjection(Projection proj) {
    // // Settin the list to null causes the list to rebuild every clock tick.
    // // eventGraphicList = null;
    // super.setProjection(proj);
    // }

    /**
     * Creates the OMGraphic list with graticule lines.
     */
    public synchronized OMGraphicList prepare() {
        Projection proj = getProjection();

        OMGraphicList graphicList = getList();
        if (getHeight() > 0) {
            if (graphicList == null) {
                graphicList = new OMGraphicList();
            } else {
                graphicList.clear();
            }
            
            if (drape == null) {
                drape = new TimeDrape(0, 0, -1, -1);
                drape.setFillPaint(Color.gray);
                drape.setVisible(false);
            }
            drape.generate(proj);
            graphicList.add(drape);

            graphicList.add(constructTimeLines(proj));
            graphicList.add(getCurrentTimeMarker(proj));

            // if new events are fetched, new rating areas and play filters are
            // created here.
            OMGraphicList eventList = getEventList(proj);

            ratingAreas.generate(proj);
            playFilter.generate(proj);

            graphicList.add(playFilter);
            graphicList.add(eventList);
            graphicList.add(getSelectionRectangle(proj));
            graphicList.add(ratingAreas);
        }
        return graphicList;
    }

    protected TimeHashFactory timeHashFactory;

    /**
     * 
     * @return OMGraphicList new graphic list
     */
    protected OMGraphicList constructTimeLines(Projection projection) {

        // if (timeLinesList == null) {
        OMGraphicList tll = new OMGraphicList();

        if (timeHashFactory == null) {
            timeHashFactory = new TimeHashFactory();
        }

        tll.add(timeHashFactory.getHastMarks(projection));

        if (preTime != null) {
            preTime.generate(projection);
            tll.add(preTime);
        }

        if (postTime != null) {
            postTime.generate(projection);
            tll.add(postTime);
        }

        timeLinesList = tll;

        return tll;
    }

    public SelectionArea getSelectionRectangle(Projection proj) {
        if (selectionRect == null) {
            selectionRect = new SelectionArea();
//            if (eventListPresenter != null) {
//                selectionRect.setFillPaint(EventListPresenter.selectColor);
//            }
        }

        selectionRect.generate(proj);
        return selectionRect;
    }

    protected OMGraphicList currentTimeMarker;
    protected SelectionArea.PreTime preTime;
    protected SelectionArea.PostTime postTime;

    protected OMGraphic getCurrentTimeMarker(Projection proj) {
        if (currentTimeMarker == null) {
            currentTimeMarker = new CurrentTimeMarker();
        }
        currentTimeMarker.generate(proj);
        return currentTimeMarker;
    }

    protected final static String ATT_KEY_EVENT = "att_key_event";

    protected OMGraphicList getEventList(Projection projection) {

        if (eventGraphicList == null) {
//            if (eventListPresenter != null) {
//                eventGraphicList = getEventList(eventListPresenter.getActiveEvents(),
//                        projection);
//
//                // As long as we feel the need to recreate the event markers,
//                // let's re-evaluate the annotations.
//                evaluateEventAttributes();
//            } else {
//                eventGraphicList = new OMGraphicList();
//            }
            eventGraphicList = new OMGraphicList();
        } else {
            eventGraphicList.generate(projection);
        }

        return eventGraphicList;
    }

    protected OMGraphicList getEventList(Iterator it, Projection projection) {
        OMGraphicList eventGraphicList = new OMGraphicList();
        if (projection != null) {

            BasicStroke symbolStroke = new BasicStroke(2);

            while (it.hasNext()) {
                Object object = it.next();

//                if (object instanceof AAREvent) {
//                    AAREvent event = (AAREvent) object;
//
//                    long time = event.getTimeStamp() - gameStartTime;
//                    float lon = forwardProjectMillis(time);
//                    EventMarkerLine currentLine = new EventMarkerLine(0f, lon, 6);
//                    currentLine.setLinePaint(Color.black);
//                    currentLine.setStroke(symbolStroke);
//                    currentLine.generate(projection);
//                    currentLine.putAttribute(ATT_KEY_EVENT, event);
//                    eventGraphicList.addOMGraphic(currentLine);
//                }
            }
        }
        return eventGraphicList;
    }

    public class EventMarkerLine extends OMLine {
        protected int heightRatioSetting;
        protected byte symbolHeight;

        public EventMarkerLine(float lat, float lon, int heightRatioSetting) {
            super(lat, lon, 0, 1, 0, -1);
            this.heightRatioSetting = heightRatioSetting;
        }

        public boolean generate(Projection proj) {
            byte testSH = (byte) (proj.getHeight() * 2 / heightRatioSetting);
            if (testSH != symbolHeight) {
                int[] pts = getPts();
                int symbolHeight = proj.getHeight() / heightRatioSetting;
                pts[1] = symbolHeight;
                pts[3] = -symbolHeight;
                this.symbolHeight = (byte) symbolHeight;
            }

            return super.generate(proj);
        }
    }

    // ----------------------------------------------------------------------
    // GUI
    // ----------------------------------------------------------------------

    /** The user interface palette for the DTED layer. */
    protected Box palette = null;

    /** Creates the interface palette. */
    public java.awt.Component getGUI() {

        if (palette == null) {
            logger.fine("creating Palette.");

            palette = Box.createVerticalBox();

            JPanel subbox3 = new JPanel(new GridLayout(0, 1));

            JButton setProperties = new JButton(i18n.get(TimelineLayer.class,
                    "setProperties",
                    "Preferences"));
            setProperties.setActionCommand(DisplayPropertiesCmd);
            setProperties.addActionListener(this);
            subbox3.add(setProperties);

            palette.add(subbox3);
        }
        return palette;
    }

    public void drawingComplete(OMGraphic omg, OMAction action) {

        if (!doAction(omg, action)) {
            // null OMGraphicList on failure, should only occur if
            // OMGraphic is added to layer before it's ever been
            // on the map.
            setList(new OMGraphicList());
            doAction(omg, action);
        }

        repaint();
    }

    // public void select(OMGraphicList list) {
    // if (list != null && list.size() > 0) {
    // OMGraphic omg = list.getOMGraphicAt(0);
    // }
    // }

    public boolean isSelectable(OMGraphic omg) {
        // return (drawingTool != null && drawingTool.canEdit(omg.getClass()));
        return false;
    }

    // ----------------------------------------------------------------------

    // ActionListener interface implementation
    // ----------------------------------------------------------------------

    /**
     * Used just for the redraw button.
     */
    public void actionPerformed(ActionEvent e) {
        super.actionPerformed(e);
        String command = e.getActionCommand();

        if (command == RedrawCmd) {
            // redrawbutton
            if (isVisible()) {
                doPrepare();
            }
        }
    }

    public String getName() {
        return "TimelineLayer";
    }

    protected void setTimeBounds(long start, long end) {
        if (gameStartTime != start || gameEndTime != end) {
            gameStartTime = start;
            gameEndTime = end;

            preTime = new SelectionArea.PreTime(0);
            postTime = new SelectionArea.PostTime(end - start);

            if (logger.isLoggable(Level.FINE)) {
                logger.fine("gst: " + gameStartTime + ", get: " + gameEndTime
                        + ", bounds of " + postTime);
            }

            setMapBeanMaxScale(true);
        }
    }

    /*
     * @see java.beans.PropertyChangeListener#propertyChange(java.beans.PropertyChangeEvent)
     */
    public void propertyChange(PropertyChangeEvent evt) {
        String propertyName = evt.getPropertyName();

        if (propertyName == Clock.TIME_BOUNDS_PROPERTY) {
            TimeBounds tb = (TimeBounds) evt.getNewValue();
            setTimeBounds(tb.getStartTime(), tb.getEndTime());
        } else if (propertyName == Clock.TIMER_STATUS_PROPERTY) {
            TimeEvent te = (TimeEvent) evt.getNewValue();

            if (checkAndSetForNoTime(te)) {
                return;
            }

            Clock clock = (Clock) te.getSource();
            setTimeBounds(clock.getStartTime(), clock.getEndTime());

            String timeEventType = te.getTimerStatus();

            if (timeEventType == TimeConstants.TIMER_STEP_FORWARD
                    || timeEventType == TimeConstants.TIMER_STEP_BACKWARD
                    || timeEventType == Clock.TIME_SET_STATUS) {
                currentTime = te.getSystemTime() - gameStartTime;
                centerDelegate.fireCenter(0, forwardProjectMillis(currentTime));
                timeLinesList = null;
                doPrepare();

            } else if (timeEventType == Clock.TIMER_TIME_STATUS) {
                // Checking for a running clock prevents a time status
                // update after the clock is stopped. The
                // AudioFileHandlers don't care about the current time
                // if it isn't running.
                if (clock.isRunning()) {
                    long currentTime = te.getSystemTime() - gameStartTime;

                    if (playFilter.reactToCurrentTime(currentTime,
                            clock,
                            gameStartTime)) {
                        this.currentTime = currentTime;
                        timeLinesList = null;
                        centerDelegate.fireCenter(0,
                                forwardProjectMillis(currentTime));
                    }
                }
            }
//        } else if (propertyName == EventListPresenter.ActiveEventsProperty) {
//            eventGraphicList = null;
//            logger.fine("EventListPresenter updated event list, calling doPrepare() "
//                    + evt.getNewValue());
//            doPrepare();
//        } else if (propertyName == AAREventSelectionCoordinator.EventsSelectedProperty) {
//            setSelectionRectangleToEvents();
//        } else if (propertyName == EventListPresenter.EventAttributesUpdatedProperty) {
//            evaluateEventAttributes();
//            doPrepare();
        } else if (propertyName == TimePanel.PlayFilterProperty) {
            boolean inUse = ((Boolean) evt.getNewValue()).booleanValue();
            playFilter.setInUse(inUse);
            firePropertyChange(PlayFilterProperty,
                    null,
                    new Boolean(playFilter.size() > 0 || !inUse));
        }
    }

    protected boolean checkAndSetForNoTime(TimeEvent te) {
        boolean isNoTime = te == TimeEvent.NO_TIME;
        if (drape != null) {
            drape.setVisible(isNoTime);
            repaint();
        }

        return isNoTime;
    }

    double snapToEvent(double lon) {

        double retVal = lon;
        double minDiff = Double.MAX_VALUE;

//        if (eventListPresenter != null) {
//
//            for (Iterator it = eventListPresenter.getAllEvents(); it.hasNext();) {
//
//                Object object = it.next();
//
//                if (object instanceof AAREvent) {
//                    AAREvent event = (AAREvent) object;
//
//                    long time = event.getTimeStamp() - gameStartTime;
//                    float timeMinutes = forwardProjectMillis(time);
//
//                    if (Math.abs(timeMinutes - lon) < minDiff) {
//                        minDiff = Math.abs(timeMinutes - lon);
//                        retVal = timeMinutes;
//                    }
//                }
//            }
//        }

        return retVal;
    }

    public MapMouseListener getMapMouseListener() {
        return this;
    }

    public String[] getMouseModeServiceList() {
        return getMouseModeIDsForEvents();
    }

    public boolean mousePressed(MouseEvent e) {
        doubleClick = false;
        updateMouseTimeDisplay(e);
        // Use current projection to determine rect top and bottom
        Projection projection = getProjection();
        // Get latLong from mouse, and then snap to nearest event...
        Point2D latLong = projection.inverse(e.getPoint());

        Point2D ul = projection.getUpperLeft();
        Point2D lr = projection.getLowerRight();
        double lon = latLong.getX();

        float up = (float) ul.getY();
        float down = (float) lr.getY();

        lon = snapToEvent(lon);

        selectionRect.setVisible(false);
        selectionRect.setLocation(up,
                (float) lon,
                down,
                (float) lon,
                OMGraphic.LINETYPE_STRAIGHT);
        selectionRect.generate(projection);

        downLon = lon;

        repaint();
        return true;
    }

    protected void selectEventForMouseEvent(MouseEvent e) {
        // Handle a single click, select event if close
        if (e != null && eventGraphicList != null) {
            OMGraphic omg = eventGraphicList.findClosest((int) e.getX(),
                    (int) e.getY(),
                    4);

            if (omg != null) {
//                AAREvent sourceEvent = (AAREvent) omg.getAttribute(ATT_KEY_EVENT);
//                if (sourceEvent != null) {
//                    sourceEvent.putAttribute(AAREvent.ATT_KEY_SELECTED,
//                            AAREvent.ATT_VAL_SELECTED);
//                    Vector eventList = new Vector();
//                    eventList.add(sourceEvent);
//                    aesc.eventsSelected(eventList);
//                }
            }
        }
        doubleClick = false;
    }

    public boolean mouseReleased(MouseEvent e) {
        updateMouseTimeDisplay(e);
        handleEventSelection();
        return true;
    }

    double downLon;
    boolean doubleClick = false;

    public boolean mouseClicked(MouseEvent e) {
        if (e.getClickCount() >= 2) {
            selectEventForMouseEvent(e);
            doubleClick = true;
            return true;
        }

        double lon = updateMouseTimeDisplay(e);

        if (clock != null) {
            clock.setTime(gameStartTime + inverseProjectMillis(lon));
        }

        selectionRect.setLocation(lon, lon);
        selectionRect.setVisible(false);

        return true;
    }

    protected double updateMouseTimeDisplay(MouseEvent e) {
        Projection proj = getProjection();
        Point2D latLong = proj.inverse(e.getPoint());
        double lon =  latLong.getX();
        double endTime = forwardProjectMillis(gameEndTime - gameStartTime);
        if (lon < 0) {
            lon = 0;
        } else if (lon > endTime) {
            lon = endTime;
        }

        long offsetMillis = inverseProjectMillis(lon);

        updateMouseTimeDisplay(new Long(offsetMillis));

        return lon;
    }

    public void updateMouseTimeDisplay(Long offsetMillis) {
        firePropertyChange(MouseTimeProperty, null, offsetMillis);
    }

    protected void updateEventDetails(MouseEvent e) {
        String details = "";
        if (e != null && eventGraphicList != null) {
            OMGraphic omg = eventGraphicList.findClosest((int) e.getX(),
                    (int) e.getY(),
                    4);

            if (omg != null) {
//                AAREvent sourceEvent = (AAREvent) omg.getAttribute(ATT_KEY_EVENT);
//                if (sourceEvent != null) {
//                    details = sourceEvent.getDescription();
//                }
            }
        }

        firePropertyChange(EventDetailsProperty, null, details);

    }

    protected void updateEventDetails() {
        updateEventDetails(null);
    }

    public void mouseEntered(MouseEvent e) {}

    public void mouseExited(MouseEvent e) {
        firePropertyChange(MouseTimeProperty, null, new Long(-1));
        updateEventDetails();
    }

    public boolean mouseDragged(MouseEvent e) {
        updateMouseTimeDisplay(e);
        updateEventDetails(e);
        // Get latLong from mouse, and set E side of current select rect...
        Projection proj = getProjection();
        Point2D latLong = proj.inverse(e.getPoint());
        double lon = snapToEvent(latLong.getX());

        float west = (float) Math.min(downLon, lon);
        float east = (float) Math.max(downLon, lon);

        selectionRect.setVisible(true);
        selectionRect.setLocation(west, east);
        selectionRect.generate(proj);
        repaint();
        return true;
    }

    public boolean mouseMoved(MouseEvent e) {
        updateMouseTimeDisplay(e);
        updateEventDetails(e);
        return true;
    }

    public void mouseMoved() {
        updateEventDetails();
    }

    protected List handleEventSelection() {
        List eventList = null;
//        if (aesc != null && selectionRect != null) {
//
//            // The thing to be careful about here is that the selection
//            // Rectangle isn't where the user clicked and released. It's snapped
//            // to the visible events. It makes some weird behavior below when
//            // you try to highlight a single event, because the time for that
//            // event is the closest snapped time event, not the invisible event
//            // that may be clicked on.
//
//            boolean goodDrag = selectionRect.isVisible();
//            double lowerTime = selectionRect.getWestLon();
//            double upperTime = selectionRect.getEastLon();
//            // Convert to millis
//            long lowerTimeStamp = inverseProjectMillis((float) lowerTime);
//            long upperTimeStamp = inverseProjectMillis((float) upperTime);
//
//            boolean sameTime = lowerTimeStamp == upperTimeStamp;
//            goodDrag = goodDrag && !sameTime;
//
//            boolean labeledRangeStart = false;
//            AAREvent lastEventLabeled = null;
//
//            for (Iterator it = eventListPresenter.getAllEvents(); it.hasNext();) {
//                if (eventList == null) {
//                    eventList = new Vector();
//                }
//
//                AAREvent event = (AAREvent) it.next();
//                double timeStamp = event.getTimeStamp() - gameStartTime;
//
//                // Don't forget, need to go through all of the events, not just
//                // the ones lower than the upper time stamp, because we need to
//                // set the selected flag to null for all of them and then only
//                // reset the ones that actually are selected.
//                event.putAttribute(AAREvent.ATT_KEY_SELECTED, null);
//
//                if (goodDrag && timeStamp >= lowerTimeStamp
//                        && timeStamp <= upperTimeStamp) {
//                    eventList.add(event);
//
//                    // Needs to be updated to put ATT_VAL_SELECTED_START_RANGE,
//                    // ATT_VAL_SELECTED_END_RANGE, or just ATT_VAL_SELECTED
//                    if (!labeledRangeStart && lowerTimeStamp != upperTimeStamp) {
//                        event.putAttribute(AAREvent.ATT_KEY_SELECTED,
//                                AAREvent.ATT_VAL_SELECTED_START_RANGE);
//                        labeledRangeStart = true;
//                    } else {
//                        event.putAttribute(AAREvent.ATT_KEY_SELECTED,
//                                AAREvent.ATT_VAL_SELECTED);
//                    }
//                    lastEventLabeled = event;
//                } else if (sameTime && timeStamp == lowerTimeStamp) {
//
//                    // This code just returns the closest visible snapped time
//                    // event.
//                    // event.putAttribute(AAREvent.ATT_KEY_SELECTED,
//                    // AAREvent.ATT_VAL_SELECTED);
//                    // eventList.add(event);
//
//                    // I guess this is OK when a visible event is clicked on,
//                    // but it's not when a non-visible event is clicked on.
//                }
//            }
//
//            if (labeledRangeStart && lastEventLabeled != null) {
//                lastEventLabeled.putAttribute(AAREvent.ATT_KEY_SELECTED,
//                        AAREvent.ATT_VAL_SELECTED_END_RANGE);
//            }
//
//            aesc.eventsSelected(eventList);
//        }

        return eventList;
    }

    protected void evaluateEventAttributes() {
        ratingAreas.clear();
        playFilter.clear();
        SelectionArea.RatingArea currentRatingArea = null;
        SelectionArea.PlayFilterSection currentPlayFilter = null;

//        if (eventListPresenter != null) {
//            for (Iterator it = eventListPresenter.getAllEvents(); it.hasNext();) {
//                AAREvent aare = (AAREvent) it.next();
//                String rating = (String) aare.getAttribute(AAREvent.ATT_KEY_RATING);
//                Object playFilterObj = aare.getAttribute(AAREvent.ATT_KEY_PLAY_FILTER);
//                long timeStamp = aare.getTimeStamp() - gameStartTime;
//
//                if (rating != null) {
//                    if (currentRatingArea != null
//                            && !currentRatingArea.isRating(rating)) {
//                        currentRatingArea = null;
//                    }
//
//                    if (currentRatingArea == null) {
//                        currentRatingArea = new SelectionArea.RatingArea(timeStamp, rating);
//                        ratingAreas.add(currentRatingArea);
//                    }
//
//                    currentRatingArea.addTime(timeStamp);
//
//                } else if (currentRatingArea != null) {
//                    currentRatingArea = null;
//                }
//
//                if (playFilterObj != null) {
//                    if (currentPlayFilter != null) {
//                        currentPlayFilter.addTime(timeStamp);
//                    } else {
//                        currentPlayFilter = new SelectionArea.PlayFilterSection(timeStamp);
//                        // logger.info("adding play filter section to play
//                        // filter");
//                        playFilter.add(currentPlayFilter);
//                    }
//                } else {
//                    currentPlayFilter = null;
//                }
//
//            }
//
//            OMGraphicList list = getList();
//            if (list != null && list.isVisible()) {
//                firePropertyChange(PlayFilterProperty,
//                        null,
//                        new Boolean(!playFilter.isInUse()
//                                || playFilter.size() > 0));
//            }
//
//        }
    }

    protected void setSelectionRectangleToEvents() {
//        if (aesc != null) {
//            selectionRect = getSelectionRectangle(getProjection());
//
//            double lowerTime = Double.POSITIVE_INFINITY;
//            double upperTime = Double.NEGATIVE_INFINITY;
//
//            for (Iterator it = eventListPresenter.getAllEvents(); it.hasNext();) {
//                AAREvent event = (AAREvent) it.next();
//
//                if (event.getAttribute(AAREvent.ATT_KEY_SELECTED) != null) {
//                    // Convert to minutes for selectRect bounds
//                    double timeStamp = (double) forwardProjectMillis(event.getTimeStamp()
//                            - gameStartTime);
//                    if (timeStamp < lowerTime) {
//                        lowerTime = timeStamp;
//                    }
//                    if (timeStamp > upperTime) {
//                        upperTime = timeStamp;
//                    }
//                }
//            }
//
//            if (upperTime != Double.NEGATIVE_INFINITY
//                    && lowerTime != Double.POSITIVE_INFINITY) {
//                selectionRect.setLocation((float) lowerTime, (float) upperTime);
//                selectionRect.setVisible(true);
//                selectionRect.generate(getProjection());
//            } else {
//                selectionRect.setVisible(false);
//            }
//        }
        repaint();
    }

    public static class SelectionArea extends com.bbn.openmap.omGraphics.OMRect {
        public SelectionArea() {
            setRenderType(OMRect.RENDERTYPE_LATLON);
        }

        public void setLocation(double left, double right) {
            super.setLocation(0f, left, 0f, right, OMRect.LINETYPE_STRAIGHT);
        }

        public boolean generate(Projection proj) {
            updateY(proj);
            return super.generate(proj);
        }

        protected void updateY(Projection proj) {
            // The difference here is that the upper and lower bounds are
            // determined by the projection.
            lat1 = (float) proj.getUpperLeft().getY();
            lat2 = (float) proj.getLowerRight().getY();
        }

        public static class PreTime extends SelectionArea {
            public PreTime(long time) {
                super();
                lon2 = forwardProjectMillis(time);
                setFillPaint(tint);
                setLinePaint(tint);
            }

            public void setLocation() {}

            public boolean generate(Projection proj) {
                // The difference here is that the vertical bounds are
                // determined the starting time and all times before that.
                float ulx = (float) proj.getUpperLeft().getX();
                if (ulx >= lon2) {
                    lon1 = lon2;
                } else {
                    lon1 = ulx;
                }

                return super.generate(proj);
            }
        }

        public static class PostTime extends SelectionArea {

            public PostTime(long time) {
                super();
                lon1 = forwardProjectMillis(time);
                setFillPaint(tint);
                setLinePaint(tint);
            }

            public void setLocation() {}

            public boolean generate(Projection proj) {
                // The difference here is that the vertical bounds are
                // determined the end time and all times after that.
                float lrx = (float) proj.getLowerRight().getX();
                if (lrx <= lon1) {
                    lon2 = lon1;
                } else {
                    lon2 = lrx;
                }

                return super.generate(proj);
            }
        }

        public static class RatingArea extends SelectionArea {
            protected String rating;
            protected static Color goodColor = new Color(0x9900ff00, true);
            protected static Color badColor = new Color(0x99ff0000, true);

            public RatingArea(long time, String rating) {
                super();
                this.rating = rating;
                double timef = forwardProjectMillis(time);
                setLocation(timef, timef);
                Color ratingColor = badColor;
//                if (rating.equals(AAREvent.ATT_VAL_GOOD_RATING)) {
//                    ratingColor = goodColor;
//                }

                setLinePaint(ratingColor);
                setFillPaint(ratingColor);
            }

            public boolean isRating(String rating) {
                return this.rating == rating
                        || this.rating.equalsIgnoreCase(rating);
            }

            public void addTime(long timeToAdd) {
                double time = forwardProjectMillis(timeToAdd);
                double east = getEastLon();
                double west = getWestLon();
                boolean updated = false;
                if (time < west) {
                    west = time;
                    updated = true;
                }

                if (time > east) {
                    east = time;
                    updated = true;
                }

                if (updated) {
                    setLocation(west, east);
                }
            }
        }

        public static class PlayFilterSection extends SelectionArea {
            protected static Color color = new Color(0x99000000, true);
            protected String idString;

            public PlayFilterSection(long time) {
                super();
                double timef = forwardProjectMillis(time);
                setLocation(timef, timef);
                setLinePaint(color);
                setFillPaint(color);
            }

            /**
             * Checks time in relation to held times.
             * 
             * @param timel time in unprojected milliseconds, offset from game
             *        start time.
             * @return 0 if time is within bounds, -1 if time is before bounds,
             *         1 if time is after bounds.
             */
            public int isWithin(long timel) {
                double time = forwardProjectMillis(timel);
                int ret = -1;
                if (time >= getWestLon()) {
                    ret++;
                }
                if (time > getEastLon()) {
                    ret++;
                }
                return ret;
            }

            protected void updateY(Projection proj) {
                lat1 = (float) proj.getUpperLeft().getY();
                Point2D lrpt = proj.inverse(0, proj.getHeight() / 8);
                lat2 = (float) lrpt.getY();
                idString = null;
            }

            public void addTime(long timeToAdd) {
                double time = forwardProjectMillis(timeToAdd);
                double east = getEastLon();
                double west = getWestLon();
                boolean updated = false;
                if (time < west) {
                    west = time;
                    updated = true;
                }

                if (time > east) {
                    east = time;
                    updated = true;
                }

                if (updated) {
                    setLocation(west, east);
                    idString = null;
                }
            }

            public String toString() {
                if (idString == null) {
                    idString = "PlayFilterSection[" + getWestLon() + ","
                            + getEastLon() + "]";
                }

                return idString;
            }
        }
    }

    public static class CurrentTimeMarker extends OMGraphicList {
        protected OMRaster upperMark;
        protected OMRaster lowerMark;
        protected OMLine startingLine;
        int iconSize = 16;
        int lastHeight = 0;
        int lastWidth = 0;

        public CurrentTimeMarker() {

            DrawingAttributes da = new DrawingAttributes();

            da.setFillPaint(tint);
            da.setLinePaint(tint);
            IconPart ip = new BasicIconPart(new Polygon(new int[] { 50, 90, 10,
                    50 }, new int[] { 10, 90, 90, 10 }, 4), da);
            ImageIcon thumbsUpImage = OMIconFactory.getIcon(iconSize,
                    iconSize,
                    ip);

            lowerMark = new OMRaster(0, 0, thumbsUpImage);

            ip = new BasicIconPart(new Polygon(new int[] { 10, 90, 50, 10 }, new int[] {
                    10, 10, 90, 10 }, 4), da);
            ImageIcon thumbsDownImage = OMIconFactory.getIcon(iconSize,
                    iconSize,
                    ip);

            upperMark = new OMRaster(0, 0, thumbsDownImage);

            startingLine = new OMLine(0, 0, 0, 0);
            da.setTo(startingLine);
            add(startingLine);
            add(lowerMark);
            add(upperMark);
        }

        public boolean generate(Projection proj) {
            int height = proj.getHeight();
            int width = proj.getWidth();

            if (height != lastHeight || width != lastWidth) {
                lastHeight = height;
                lastWidth = width;

                int halfX = (int) (width / 2);

                upperMark.setX(halfX - iconSize / 2);
                upperMark.setY(0);
                lowerMark.setX(halfX - iconSize / 2);
                lowerMark.setY(height - iconSize);
                int[] pts = startingLine.getPts();
                pts[0] = halfX;
                pts[1] = 0 + iconSize;
                pts[2] = halfX;
                pts[3] = height - iconSize;
            }
            return super.generate(proj);
        }
    }

    public static class TimeHashFactory {

        List hashMarks = new ArrayList(5);
        TimeHashMarks current;

        public TimeHashFactory() {
            hashMarks.add(new TimeHashMarks.Seconds());
            hashMarks.add(new TimeHashMarks.Minutes());
            hashMarks.add(new TimeHashMarks.Hours());
            hashMarks.add(new TimeHashMarks.Days());
            hashMarks.add(new TimeHashMarks.Years());
        }

        public OMGraphicList getHastMarks(Projection proj) {

            Point2D ul = proj.getUpperLeft();
            Point2D lr = proj.getLowerRight();

            // timeSpan in minutes
            double timeSpan = lr.getX() - ul.getX();

            TimeHashMarks thm = null;
            for (Iterator it = hashMarks.iterator(); it.hasNext();) {
                TimeHashMarks cthm = (TimeHashMarks) it.next();
                if (cthm.passesThreshold(timeSpan)) {
                    thm = cthm;
                } else {
                    break;
                }
            }

            if (current != null) {
                current.clear();
            }
            if (thm != current) {
                current = thm;
            }

            current.generate(proj, timeSpan);

            return current;
        }
    }

    public static class PlayFilter extends OMGraphicList {
        protected boolean inUse = false;
        String currentlyPlaying = null;

        public PlayFilter() {}

        public boolean reactToCurrentTime(long currentTime, Clock clock,
                                          long gameStartTime) {
            boolean ret = !inUse;
            if (inUse) {
                // logger.info("checking " + size() + " sections");

                for (Iterator it = iterator(); it.hasNext();) {
                    PlayFilterSection pfs = (PlayFilterSection) it.next();
                    int where = pfs.isWithin(currentTime);
                    if (where == 0) {
                        ret = true;
                        currentlyPlaying = pfs.toString();
                        // logger.info("where == 0, setting pfs " +
                        // currentlyPlaying);
                        break;
                    } else if (where > 0) {

                        if ((currentlyPlaying != null && (currentlyPlaying.equals(pfs.toString())))
                                || !it.hasNext()) {
                            // logger.info("where > 0, same pfs, stopping clock
                            // " + pfs);
                            clock.setTime(gameStartTime
                                    + inverseProjectMillis(pfs.getEastLon()));
                            clock.stopClock();
                            currentlyPlaying = null;
                            break;
                        } else {
                            // logger.info("where > 0, not the same pfs " + pfs
                            // + ", " + currentlyPlaying);
                        }

                        continue;
                    } else {
                        // logger.info("where < 0, jumping clock " + pfs);
                        clock.setTime(gameStartTime
                                + inverseProjectMillis(pfs.getWestLon()));
                        break;
                    }
                }
            }

            return ret;
        }

        public boolean isInUse() {
            return inUse;
        }

        public void setInUse(boolean inUse) {
            this.inUse = inUse;
        }
    }

    public abstract static class TimeHashMarks extends OMGraphicList {
        protected String annotation;
        protected double unitPerMinute;

        protected TimeHashMarks(String annotation, double unitPerMinute) {
            this.annotation = annotation;
            this.unitPerMinute = unitPerMinute;
        }

        public abstract boolean passesThreshold(double minVisibleOnTimeLine);

        public boolean generate(Projection proj, double minSpan) {
            Point2D ul = proj.getUpperLeft();
            Point2D lr = proj.getLowerRight();
            double left = ul.getX() * unitPerMinute;
            double right = lr.getX() * unitPerMinute;
            minSpan = minSpan * unitPerMinute;

            double num = Math.floor(minSpan);
            double heightStepSize = 1;
            double stepSize = 1;
            if (num < 2) {
                stepSize = .25;
            } else if (num < 5) {
                stepSize = .5;
            } else if (num > 30) {
                stepSize = 10;
                heightStepSize = 10;
            } else if (num > 15) {
                heightStepSize = 10;
            }

            if (logger.isLoggable(Level.FINE)) {
                logger.fine("figure on needing " + num + annotation + ", "
                        + stepSize + " stepsize for " + (minSpan / stepSize)
                        + " lines");
            }

            int height = (int) (proj.getHeight() * .2);
            double anchory = lr.getY();

            // Now, we need to baseline marks on 0, not on the left most value.
            double start = 0;
            // need to do negative times.
            if (left < 0) {
                while (start > left) {
                    start -= stepSize;
                }
            }

            while (start < left) {
                start += stepSize;
            }

            double stepStart = Math.floor(start);
            double stepEnd = Math.ceil(right);

            for (double i = stepStart; i < stepEnd; i += stepSize) {
                double anchorx = i / unitPerMinute;

                int thisHeight = height;
                boolean doLabel = true;
                if (i % heightStepSize != 0) {
                    thisHeight /= 2;
                    doLabel = false;
                }

                OMLine currentLine = new OMLine((float) anchory, (float) anchorx, 0, 0, 0, -thisHeight);
                currentLine.setLinePaint(tint);
                currentLine.setStroke(new BasicStroke(2));
                add(currentLine);

                if (doLabel) {
                    OMText label = new OMText((float) anchory, (float) anchorx, 2, -5, (int) i
                            + annotation, OMText.JUSTIFY_LEFT);
                    label.setLinePaint(tint);
                    add(label);
                }
            }

            return super.generate(proj);
        }

        public static class Seconds extends TimeHashMarks {
            public Seconds() {
                super("s", 60);
            }

            public boolean passesThreshold(double minVisibleOnTimeLine) {
                return true;
            }

        }

        public static class Minutes extends TimeHashMarks {
            public Minutes() {
                super("m", 1);
            }

            public boolean passesThreshold(double minVisibleOnTimeLine) {
                return minVisibleOnTimeLine > 2;
            }

        }

        public static class Hours extends TimeHashMarks {
            public Hours() {
                super("h", (1d / 60d));
            }

            public boolean passesThreshold(double minVisibleOnTimeLine) {
                return minVisibleOnTimeLine / 60 > 3;
            }
        }

        public static class Days extends TimeHashMarks {
            public Days() {
                super("d", (1d / 60d / 24d));
            }

            public boolean passesThreshold(double minVisibleOnTimeLine) {
                return minVisibleOnTimeLine / 60 / 24 > 2;
            }

        }

        public static class Years extends TimeHashMarks {
            public Years() {
                super("y", (1d / 60d / 24d / 365d));
            }

            public boolean passesThreshold(double minVisibleOnTimeLine) {
                return minVisibleOnTimeLine / 60 / 24 / 365 > 1;
            }

        }

    }

    protected void setMapBeanMaxScale(boolean setScaleToMax) {
        float scale = (float) (TimeSliderLayer.magicScaleFactor
                * (double) forwardProjectMillis(gameEndTime - gameStartTime) / getProjection().getWidth());

        MapBean mb = (MapBean) ((MapHandler) getBeanContext()).get(com.bbn.openmap.MapBean.class);
        ((Cartesian) mb.getProjection()).setMaxScale(scale);

        if (setScaleToMax) {
            mb.setScale(scale);
        }
    }

    public void componentHidden(ComponentEvent e) {}

    public void componentMoved(ComponentEvent e) {}

    public void componentResized(ComponentEvent e) {
        setMapBeanMaxScale(false);
    }

    public void componentShown(ComponentEvent e) {}

    public void paint(Graphics g) {
        try {
            super.paint(g);
        } catch (Exception e) {
            if (logger.isLoggable(Level.FINE)) {
                logger.warning(e.getMessage());
                e.printStackTrace();
            }
        }
    }
}