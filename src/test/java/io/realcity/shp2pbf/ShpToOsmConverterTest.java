package io.realcity.shp2pbf;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.type.Name;
import org.opengis.feature.type.PropertyType;
import org.openstreetmap.osmosis.core.domain.v0_6.CommonEntityData;
import org.openstreetmap.osmosis.core.domain.v0_6.OsmUser;
import org.openstreetmap.osmosis.core.domain.v0_6.Tag;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;
import org.openstreetmap.osmosis.core.domain.v0_6.WayNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ShpToOsmConverterTest {

    @Mock
    private SimpleFeature simpleFeature;

    @Test
    public void generateWayTest() {
        LineString lineString = Mockito.mock(LineString.class);

        Coordinate[] coordinates = Arrays.asList(
                new Coordinate(1, 1),
                new Coordinate(2, 2)
        ).toArray(Coordinate[]::new);

        when(lineString.getCoordinates()).thenReturn(coordinates);

        when(simpleFeature.getProperties()).thenReturn(new ArrayList<>());

        ShpToOsmConverter converter = new ShpToOsmConverter();
        Way way = converter.createWayForLineString(lineString, simpleFeature, "");

        List<WayNode> wayNodes = way.getWayNodes();

        Assertions.assertEquals(2, wayNodes.size());

        Assertions.assertEquals(1, wayNodes.get(0).getLatitude(), 0.1);
        Assertions.assertEquals(1, wayNodes.get(0).getLongitude(), 0.1);
        Assertions.assertEquals(2, wayNodes.get(1).getLatitude(), 0.1);
        Assertions.assertEquals(2, wayNodes.get(1).getLongitude(), 0.1);
    }

    @Test
    public void mergeCommonPointsInTwoWaysTest() {
        LineString firstLineString = Mockito.mock(LineString.class);

        Coordinate[] firstCoordinates = Arrays.asList(
                new Coordinate(1, 1),
                new Coordinate(2, 2)
        ).toArray(Coordinate[]::new);

        when(firstLineString.getCoordinates()).thenReturn(firstCoordinates);

        LineString secondLineString = Mockito.mock(LineString.class);

        Coordinate[] secondCoordinates = Arrays.asList(
                new Coordinate(2, 2),
                new Coordinate(3, 3)
        ).toArray(Coordinate[]::new);

        when(secondLineString.getCoordinates()).thenReturn(secondCoordinates);

        when(simpleFeature.getProperties()).thenReturn(new ArrayList<>());

        ShpToOsmConverter converter = new ShpToOsmConverter();
        Way way1 = converter.createWayForLineString(firstLineString, simpleFeature, "");
        Way way2 = converter.createWayForLineString(secondLineString, simpleFeature, "");

        Assertions.assertEquals(2, way1.getWayNodes().size());
        Assertions.assertEquals(2, way2.getWayNodes().size());

        Assertions.assertEquals(way1.getWayNodes().get(1).getNodeId(), way2.getWayNodes().get(0).getNodeId());
    }

    @Test
    public void copyTagsTest() {
        String key = "Key";
        String value = "Value";
        Property property = createMockedProperty(key, value);

        when(simpleFeature.getProperties()).thenReturn(Collections.singletonList(property));

        CommonEntityData commonEntityData = new CommonEntityData(1, 1, new Date(), OsmUser.NONE, 0);

        ShpToOsmConverter converter = new ShpToOsmConverter();
        converter.copyTags(simpleFeature, "", commonEntityData);

        Assertions.assertEquals(1, commonEntityData.getTags().size());

        Tag[] tags = commonEntityData.getTags().toArray(Tag[]::new);
        Tag tag = tags[0];
        Assertions.assertEquals(key, tag.getKey());
        Assertions.assertEquals(value, tag.getValue());
    }

    private Property createMockedProperty(String key, String value) {
        Property property = Mockito.mock(Property.class);

        PropertyType propertyType = Mockito.mock(PropertyType.class);
        when(property.getType()).thenReturn(propertyType);

        Name name = Mockito.mock(Name.class);
        when(propertyType.getName()).thenReturn(name);
        when(name.toString()).thenReturn(key);

        lenient().when(property.getValue()).thenReturn(value);

        return property;
    }

    @Test
    public void excludeGeometryTypeTagTest() {
        String key = "Key";
        String value = "Value";
        Property property = createMockedProperty(key, value);

        String key2 = "GeometryType";
        String value2 = "GeometryValue";
        Property property2 = createMockedProperty(key2, value2);

        when(simpleFeature.getProperties()).thenReturn(Arrays.asList(property, property2));

        CommonEntityData commonEntityData = new CommonEntityData(1, 1, new Date(), OsmUser.NONE, 0);

        ShpToOsmConverter converter = new ShpToOsmConverter();
        converter.copyTags(simpleFeature, key2, commonEntityData);

        Assertions.assertEquals(1, commonEntityData.getTags().size());

        Tag[] tags = commonEntityData.getTags().toArray(Tag[]::new);
        Tag tag = tags[0];
        Assertions.assertNotEquals(key2, tag.getKey());
        Assertions.assertNotEquals(value2, tag.getValue());
    }
}
