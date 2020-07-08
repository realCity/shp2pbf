package io.realcity.shp2pbf;

import com.beust.jcommander.IStringConverter;
import com.beust.jcommander.Parameter;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class CommandLineParameters {

    @Parameter(names = {"--charset"}, description = "Character set", converter = CharsetParser.class)
    public Charset charset = StandardCharsets.UTF_8;

    @Parameter(names = {"--inputFile"}, required = true, description = "Input File.")
    public File inputFile;

    @Parameter(names = {"--outputFile"}, required = true, description = "Output File.")
    public File outputFile;

    private static class CharsetParser implements IStringConverter<Charset> {
        @Override
        public Charset convert(String charsetName) {
            return Charset.forName(charsetName);
        }
    }
}
