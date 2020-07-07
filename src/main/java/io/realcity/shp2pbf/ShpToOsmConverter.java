package io.realcity.shp2pbf;

import crosby.binary.osmosis.OsmosisSerializer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.geotools.data.FeatureSource;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.geometry.jts.JTS;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.index.quadtree.Quadtree;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;
import org.openstreetmap.osmosis.core.container.v0_6.NodeContainer;
import org.openstreetmap.osmosis.core.container.v0_6.WayContainer;
import org.openstreetmap.osmosis.core.domain.v0_6.CommonEntityData;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.OsmUser;
import org.openstreetmap.osmosis.core.domain.v0_6.Tag;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;
import org.openstreetmap.osmosis.core.domain.v0_6.WayNode;
import org.openstreetmap.osmosis.osmbinary.file.BlockOutputStream;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

@Slf4j
public class ShpToOsmConverter {

    private static final double EPSILON = 0.0000001;

    private long nextOsmNodeId = -1;
    private long nextWayNodeId = -1;

    private long getNextNodeId() {
        return nextOsmNodeId--;
    }

    private long getNextWayId() {
        return nextWayNodeId--;
    }

    private final Date timestamp = new Date();

    private final Quadtree nodeIndexTree = new Quadtree();
    private final List<NodeContainer> createdNodes = new ArrayList<>();
    private final List<WayContainer> createdWays = new ArrayList<>();

    public void processTypeNamesFromDataStore(ShapefileDataStore dataStore, MathTransform transform) throws IOException {
        String[] typeNames = dataStore.getTypeNames();
        for (String typeName : typeNames) {
            log.debug("Converting " + typeName);

            FeatureIterator<SimpleFeature> iterator = null;

            try {
                FeatureSource<SimpleFeatureType, SimpleFeature> featureSource = dataStore.getFeatureSource(typeName);
                FeatureCollection<SimpleFeatureType, SimpleFeature> collection = featureSource.getFeatures();
                iterator = collection.features();

                long counter = 0;
                while (iterator.hasNext()) {
                    if (counter % 10000 == 0) {
                        log.info("Converted {}/{} ways.", counter, collection.size());
                    }
                    SimpleFeature feature = iterator.next();
                    processSimpleFeature(feature, transform);
                    counter++;
                }
                log.info("Converted {}/{} ways.", counter, collection.size());
            } catch (TransformException e) {
                throw new RuntimeException("Could not transform to spherical mercator.", e);
            } finally {
                if (iterator != null) {
                    // YOU MUST CLOSE THE ITERATOR!
                    iterator.close();
                }
            }

        }
    }

    private void processSimpleFeature(SimpleFeature feature, MathTransform transform) throws TransformException {
        Geometry rawGeom = (Geometry) feature.getDefaultGeometry();

        String geometryType = rawGeom.getGeometryType();

        // Transform to spherical mercator
        Geometry geometry = JTS.transform(rawGeom, transform);

        if (!"MultiLineString".equals(geometryType)) {
            log.warn("GeometryType not supported: " + geometryType + ", skipping element: " + feature.getID());
            return;
        }

        for (int i = 0; i < geometry.getNumGeometries(); i++) {
            LineString lineString = (LineString) geometry.getGeometryN(i);
            Way way = createWayForLineString(lineString, feature, geometryType);
            createdWays.add(new WayContainer(way));
        }
    }

    Way createWayForLineString(LineString lineString, SimpleFeature feature, String geometryType) {
        Coordinate[] coordinates = lineString.getCoordinates();

        CommonEntityData wayEntityData = new CommonEntityData(getNextWayId(), 1, timestamp, OsmUser.NONE, 0);
        copyTags(feature, geometryType, wayEntityData);
        Way way = new Way(wayEntityData);

        for (int coordinateIndex = 0; coordinateIndex < coordinates.length; coordinateIndex++) {
            Coordinate coord = coordinates[coordinateIndex];

            boolean firstOrLastNode = coordinateIndex == 0 || coordinateIndex == coordinates.length - 1;
            long nodeIndex = getOrCreateNodeForCoordinate(firstOrLastNode, coord);

            WayNode wayNode = new WayNode(nodeIndex, coord.y, coord.x);
            way.getWayNodes().add(wayNode);
        }

        return way;
    }

    private long getOrCreateNodeForCoordinate(boolean startOrEndNode, Coordinate coord) {
        Envelope queryEnvelope = Quadtree.ensureExtent(new Envelope(coord), EPSILON);
        long nodeIndex;

        if (startOrEndNode) {
            List<Node> result = nodeIndexTree.query(queryEnvelope);
            if (!result.isEmpty()) {
                for (Node resultNode : result) {
                    if (queryEnvelope.contains(resultNode.getLongitude(), resultNode.getLatitude())) {
                        return resultNode.getId();
                    }
                }
            }
        }

        nodeIndex = getNextNodeId();
        Node node = createNode(nodeIndex, coord);
        createdNodes.add(new NodeContainer(node));

        if (startOrEndNode) {
            nodeIndexTree.insert(queryEnvelope, node);
        }

        return nodeIndex;
    }

    private Node createNode(long ID, Coordinate coordinate) {
        CommonEntityData ced = new CommonEntityData(ID, 1, timestamp, OsmUser.NONE, 0);
        return new Node(ced, coordinate.y, coordinate.x);
    }

    void copyTags(SimpleFeature feature, String geometryType, CommonEntityData ced) {
        Collection<Property> properties = feature.getProperties();
        for (Property property : properties) {
            String srcKey = property.getType().getName().toString();
            if (!geometryType.equals(srcKey)) {

                Object value = property.getValue();
                if (value != null) {
                    String dirtyOriginalValue = getDirtyValue(value);

                    if (!StringUtils.isEmpty(dirtyOriginalValue)) {
                        ced.getTags().add(new Tag(srcKey, dirtyOriginalValue));
                    }
                }
            }
        }
    }

    private String getDirtyValue(Object value) {
        String dirtyOriginalValue;
        if (value instanceof Double) {
            double asDouble = (Double) value;
            double floored = Math.floor(asDouble);
            if (floored == asDouble) {
                dirtyOriginalValue = Integer.toString((int) asDouble);
            } else {
                dirtyOriginalValue = Double.toString(asDouble);
            }
        } else {
            dirtyOriginalValue = value.toString().trim();
        }
        return dirtyOriginalValue;
    }

    public void writeCreatedItemsToPbf(File outputFile) throws FileNotFoundException {
        log.info("Writing pbf file...");
        OutputStream outputStream = new FileOutputStream(outputFile);
        OsmosisSerializer osmosisSerializer = new OsmosisSerializer(new BlockOutputStream(outputStream));

        osmosisSerializer.writeEmptyHeaderIfNeeded();

        for (NodeContainer nodeContainer : createdNodes) {
            osmosisSerializer.process(nodeContainer);
        }

        for (WayContainer wayContainer : createdWays) {
            osmosisSerializer.process(wayContainer);
        }

        osmosisSerializer.complete();
        log.info("Wrote pbf.");
    }
}
