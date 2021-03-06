package building;

import geometry.ZLargestRectangleRatio;
import geometry.ZPoint;
import math.ZGeoMath;
import math.ZMath;
import org.locationtech.jts.algorithm.MinimumDiameter;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;
import processing.core.PApplet;
import transform.ZTransform;
import wblut.geom.*;
import wblut.processing.WB_Render;

import java.util.ArrayList;
import java.util.List;

/**
 * simple shop
 *
 * @author ZHANG Bai-zhou zhangbz
 * @project shopping_mall
 * @date 2021/1/5
 * @time 13:42
 */
public class SimpleShop {
    private WB_Polygon originalSite;
    private WB_Polygon generateSite;

    private WB_Point siteCenter;
    private WB_Point siteDirPoint;

    private List<WB_Polygon> buildingBases;
    private List<BasicBuilding> buildings;

    private boolean sub = false;

    /* ------------- constructor ------------- */

    public SimpleShop(WB_Polygon originalSite, List<WB_Polygon> trafficBlocks, List<WB_Polygon> subTrafficBlocks) {
        initGenerateSite(originalSite, trafficBlocks, subTrafficBlocks);
    }

    public void initGenerateSite(WB_Polygon originalSite, List<WB_Polygon> trafficBlocks, List<WB_Polygon> subTrafficBlocks) {
        setOriginalSite(originalSite);
        setGenerateSite(originalSite);
        // calculate site center
        double sumX = 0, sumY = 0;
        for (int i = 0; i < generateSite.getNumberOfPoints() - 1; i++) {
            sumX = sumX + generateSite.getPoint(i).xd();
            sumY = sumY + generateSite.getPoint(i).yd();
        }
        this.siteCenter = new WB_Point(
                sumX / (generateSite.getNumberOfPoints() - 1),
                sumY / (generateSite.getNumberOfPoints() - 1)
        );
        resetSiteDir(trafficBlocks, subTrafficBlocks);
    }

    public void initBuildings() {
        setBuildingBases(generateSite);
        setBuildings(buildingBases);
    }

    /* ------------- member function ------------- */

    /**
     * set rectangle site to generate building groups
     * if the original shape is closed to its OBB, then let OBB be it's site
     * otherwise, record the w / h ratio of the OBB and generate largest rectangle in it
     *
     * @param originalSite original site polygon
     * @return void
     */
    private void setGenerateSite(final WB_Polygon originalSite) {
        Polygon jtsSite = ZTransform.WB_PolygonToJtsPolygon(originalSite);
        Geometry obb = MinimumDiameter.getMinimumRectangle(jtsSite);
        if (obb instanceof Polygon) {
            if (jtsSite.getArea() / obb.getArea() > 0.95) {
//                Polygon obbPolygon = (Polygon) obb;
//                obbPolygon.getCoordinates()[0].distance(obb.getCoordinates()[1]);
                this.generateSite = ZTransform.jtsPolygonToWB_Polygon((Polygon) obb.buffer(-0.5));
            } else {
                double edgeLength1 = obb.getCoordinates()[0].distance(obb.getCoordinates()[1]);
                double edgeLength2 = obb.getCoordinates()[1].distance(obb.getCoordinates()[2]);
                double whRatio = edgeLength1 / edgeLength2;
                ZLargestRectangleRatio largestRect = new ZLargestRectangleRatio(originalSite, whRatio);
                largestRect.init();
                this.generateSite = largestRect.getLargestRectangle();
            }
            this.siteDirPoint = generateSite.getSegment(0).getCenter();
        } else {
            System.out.println("oriented bounding box is not a Polygon, please check input");
        }
    }

    /**
     * find the closest point to the traffic blocks
     * rebuild the generateSite by the direction of the closest point
     *
     * @param trafficBlocks    traffic blocks
     * @param subTrafficBlocks sub traffic blocks
     * @return void
     */
    public void resetSiteDir(List<WB_Polygon> trafficBlocks, List<WB_Polygon> subTrafficBlocks) {
        // rebuild site polygon by direction
        // the direction depends on the closest point to the traffic
        WB_Point closest = null;
        double minDist = Double.MAX_VALUE;
        for (WB_Polygon trafficBlock : trafficBlocks) {
            WB_Point currClosest = WB_GeometryOp.getClosestPoint2D(siteCenter, (WB_PolyLine) trafficBlock);
            double currSqDist = siteCenter.getSqDistance(currClosest);
            if (currSqDist < minDist) {
                closest = currClosest;
                minDist = currSqDist;
                sub = false;
            }
        }
        for (WB_Polygon subTrafficBlock : subTrafficBlocks) {
            WB_Point currClosest = WB_GeometryOp.getClosestPoint2D(siteCenter, (WB_PolyLine) subTrafficBlock);
            double currSqDist = siteCenter.getSqDistance(currClosest);
            if (currSqDist < minDist) {
                closest = currClosest;
                minDist = currSqDist;
                sub = true;
            }
        }
        if (closest != null) {
            ZPoint[] ray = new ZPoint[]{
                    new ZPoint(siteCenter),
                    new ZPoint(closest.sub(siteCenter))
            };
            List<WB_Point> newPoints = new ArrayList<>();
            int startIndex = 0;
            for (int i = 0; i < generateSite.getNumberSegments(); i++) {
                WB_Segment segment = generateSite.getSegment(i);
                ZPoint[] seg = new ZPoint[]{
                        new ZPoint(segment.getOrigin()),
                        new ZPoint(segment.getEndpoint()).sub(new ZPoint(segment.getOrigin()))
                };
                if (ZGeoMath.checkRaySegmentIntersection(ray, seg)) {
                    startIndex = i;
                    break;
                }
            }
            for (int i = 0; i < generateSite.getNumberOfPoints(); i++) {
                newPoints.add(generateSite.getPoint((i + startIndex) % (generateSite.getNumberOfPoints() - 1)));
            }
            this.generateSite = new WB_Polygon(newPoints);
            this.siteDirPoint = generateSite.getSegment(0).getCenter();
        }
    }


    /**
     * choose different patterns based on the area and shape of site
     *
     * @param generateSite rectangle site to generate building groups
     * @return void
     */
    private void setBuildingBases(WB_Polygon generateSite) {
        if (Math.abs(generateSite.getSignedArea()) < 150) {
            setBasePattern3(generateSite);
        } else {
            if (rectangleRatio(generateSite) < 1) {
                setBasePattern1(generateSite);
            } else {
                double random = Math.random();
                if (random > 0.6) {
                    setBasePattern3(generateSite);
                } else {
                    setBasePattern2(generateSite);
                }
            }
        }

    }

    private double rectangleRatio(WB_Polygon rect) {
        return rect.getSegment(0).getLength() / rect.getSegment(1).getLength();
    }

    /**
     * set every single building in the bases
     *
     * @param buildingBases base rectangles computed
     * @return void
     */
    private void setBuildings(List<WB_Polygon> buildingBases) {
        this.buildings = new ArrayList<>();
        if (sub) {
            for (WB_Polygon base : buildingBases) {
                buildings.add(new BasicBuilding(base, ZMath.randomInt(1.5, 2.9), ZMath.random(3, 3.5), false));
            }
        } else {
            for (WB_Polygon base : buildingBases) {
                buildings.add(new BasicBuilding(base, ZMath.randomInt(2.5, 3.5), ZMath.random(3.5, 4), false));
            }
        }
    }

    /**
     * 三等分，两边建体量
     *
     * @param generateSite rectangle site to generate bases
     * @return void
     */
    private void setBasePattern1(final WB_Polygon generateSite) {
        // assert rectangle
        this.buildingBases = new ArrayList<>();
        double[] dist = ZMath.randomArray(
                2,
                generateSite.getSegment(1).getLength() * 0.3,
                generateSite.getSegment(1).getLength() * 0.36
        );
        WB_Point p = generateSite.getSegment(1).getPoint(dist[0]);
        WB_Point[] base1 = new WB_Point[]{
                generateSite.getPoint(0),
                generateSite.getPoint(1),
                p,
                generateSite.getPoint(0).add(p.sub(generateSite.getPoint(1))),
                generateSite.getPoint(0)
        };
        WB_Point q = generateSite.getSegment(3).getPoint(dist[1]);
        WB_Point[] base2 = new WB_Point[]{
                generateSite.getPoint(2),
                generateSite.getPoint(3),
                q,
                generateSite.getPoint(2).add(q.sub(generateSite.getPoint(3))),
                generateSite.getPoint(2)
        };
        WB_Point[] base3 = new WB_Point[]{
                base1[2],
                base2[3],

        };
        buildingBases.add(new WB_Polygon(base1));
        buildingBases.add(new WB_Polygon(base2));
    }

    /**
     * 对角布置体量
     *
     * @param generateSite rectangle site to generate bases
     * @return void
     */
    private void setBasePattern2(final WB_Polygon generateSite) {
        // assert rectangle
        this.buildingBases = new ArrayList<>();
        double[] dist1 = ZMath.randomArray(
                2,
                generateSite.getSegment(0).getLength() * 0.5,
                generateSite.getSegment(0).getLength() * 0.7
        );
        double[] dist2 = ZMath.randomArray(
                2,
                generateSite.getSegment(1).getLength() * 0.85,
                generateSite.getSegment(1).getLength()
        );
//        WB_Point p = generateSite.getSegment(0).getPoint(dist1[0]);
        WB_Point p = generateSite.getSegment(0).getPoint(generateSite.getSegment(0).getLength() * 0.5 - 0.4);
        WB_Point[] base1 = new WB_Point[]{
                generateSite.getPoint(0),
                p,
                p.add(
                        generateSite.getSegment(1).getDirection().xd() * dist2[0],
                        generateSite.getSegment(1).getDirection().yd() * dist2[0]
                ),
                generateSite.getPoint(0).add(
                        generateSite.getSegment(1).getDirection().xd() * dist2[0],
                        generateSite.getSegment(1).getDirection().yd() * dist2[0]
                ),
                generateSite.getPoint(0)
        };
//        WB_Point q = generateSite.getSegment(2).getPoint(dist1[1]);
        WB_Point q = generateSite.getSegment(2).getPoint(generateSite.getSegment(2).getLength() * 0.5 - 0.4);
        WB_Point[] base2 = new WB_Point[]{
                generateSite.getPoint(2),
                q,
                q.add(
                        generateSite.getSegment(3).getDirection().xd() * dist2[1],
                        generateSite.getSegment(3).getDirection().yd() * dist2[1]
                ),
                generateSite.getPoint(2).add(
                        generateSite.getSegment(3).getDirection().xd() * dist2[1],
                        generateSite.getSegment(3).getDirection().yd() * dist2[1]
                ),
                generateSite.getPoint(2)
        };
        buildingBases.add(new WB_Polygon(base1));
        buildingBases.add(new WB_Polygon(base2));
    }

    /**
     * only one building filling the whole site
     *
     * @param
     * @return void
     */
    private void setBasePattern3(final WB_Polygon generateSite) {
        // assert rectangle
        this.buildingBases = new ArrayList<>();
        buildingBases.add(generateSite);
    }

    /* ------------- setter & getter ------------- */

    public void setOriginalSite(WB_Polygon originalSite) {
        this.originalSite = originalSite;
    }

    public List<BasicBuilding> getBuildings() {
        return buildings;
    }

    /* ------------- draw ------------- */

    public void displayBuilding(WB_Render render, PApplet app){
        app.noStroke();
        if (buildings != null) {
            for (BasicBuilding building : buildings) {
                building.display(render, app);
            }
        }
    }

    public void displaySite(WB_Render render, PApplet app) {
        app.noFill();
        app.stroke(255, 0, 0);
        if (generateSite != null) {
            render.drawPolygonEdges2D(generateSite);
            app.line(siteCenter.xf(), siteCenter.yf(), siteDirPoint.xf(), siteDirPoint.yf());
        }
    }

}
