package io.realcity.shp2pbf;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import lombok.extern.slf4j.Slf4j;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.referencing.CRS;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class Main {


    public static void main(String... args) throws IOException {
        CommandLineParameters parameters = parseCommandLineParameters(args);

        ShpToOsmConverter converter = new ShpToOsmConverter();

        File outputFile = parameters.outputFile;
        if (outputFile.createNewFile()) {
            log.info("File created: " + outputFile.getName());
        } else {
            log.info("File: " + outputFile.getName() + " will be overwritten");
        }

        ShapefileDataStore dataStore;
        MathTransform transform;
        try {
            dataStore = getDataStore(parameters.inputFile, parameters.charset);
            transform = createMathTransform(dataStore);

        } catch (IOException | FactoryException e) {
            throw new RuntimeException("Could not generate datastore or transform.", e);
        }

        converter.processTypeNamesFromDataStore(dataStore, transform);

        converter.writeCreatedItemsToPbf(outputFile);

        log.info("Finished conversion.");
    }

    private static CommandLineParameters parseCommandLineParameters(String[] args) {
        log.info("Parsing command line arguments");
        CommandLineParameters params = new CommandLineParameters();
        try {
            JCommander jCommander = new JCommander(params, null, args);
            if (params.inputFile == null || params.outputFile == null) {
                jCommander.usage();
                System.exit(1);
            }
            return params;
        } catch (ParameterException pex) {
            log.error(pex.getMessage());
            System.exit(1);
        }

        return null;
    }

    private static ShapefileDataStore getDataStore(File shapeFile, Charset charset) throws IOException {
        Map<String, Serializable> connectParameters = new HashMap<>();

        connectParameters.put("url", shapeFile.toURI().toURL());
        connectParameters.put("create spatial index", false);
        connectParameters.put("charset", charset.name());
        return (ShapefileDataStore) DataStoreFinder.getDataStore(connectParameters);
    }

    private static MathTransform createMathTransform(ShapefileDataStore dataStore) throws IOException, FactoryException {
        CoordinateReferenceSystem sourceCRS = dataStore.getSchema().getCoordinateReferenceSystem();
        CoordinateReferenceSystem targetCRS = buildTargetCRS();

        if (sourceCRS == null) {
            throw new RuntimeException("Could not determine the shapefile's projection. " +
                    "More than likely, the .prj file was not included.");
        }

        log.info("Converting from " + sourceCRS + " to " + targetCRS);
        return CRS.findMathTransform(sourceCRS, targetCRS, true);
    }

    private static CoordinateReferenceSystem buildTargetCRS() {
        try {
            return CRS.decode("EPSG:4326", true);
        } catch (FactoryException e) {
            throw new RuntimeException("Could not found CRS.", e);
        }
    }
}

