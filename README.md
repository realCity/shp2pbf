# shp2pbf

It converts `.shp` formatted files to `.osm.pbf` format. It supports only `MultiLineString` geometry.

### Parameters
* inputFile: Contains the input `.shp` file.
* outputFile: Contains the output file in `.osm.pbf` format. If the file exists, the tool overrides it.
* charset (optional): Contains the shapefile charset encoding, default is `UTF-8`.
 
### Configuration
It can be run from command line (after Maven build):
```
shp2pbf --inputFile /path/to/input.shp --outputFile /path/to/output.osm.pbf
```

### TagTransform after run

The created tags could be in wrong format, so they have to be converted. The best way to do this is with `osmosis`.

Example run:
```
osmosis --read-pbf file=output.osm.pbf --tag-transform file=TRANSFORM.xml --write-pbf file=transformed.osm.pbf
```

Transform xml example data ([TagTransform documentation](https://wiki.openstreetmap.org/wiki/Osmosis/TagTransform#Running_a_transform)):
```xml
<?xml version="1.0"?>
<translations>
  <translation>
    <name>Name transform</name>
    <description>Maps tags with old_name key to name key in ways</description>
    <match type="way">
      <tag k="old_name" match_id="n" v=".*"/>
    </match>
    <output>
      <copy-unmatched/>
      <tag from_match="n" k="name" v="{0}"/>
    </output>
  </translation>
</translations>
```
