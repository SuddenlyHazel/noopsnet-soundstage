// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.soundstage;

import net.minecraft.core.BlockPos;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

/** Offline regression for the actual client export; it does not launch Minecraft. */
public final class ShowExportProbe {
    private static final String NS = "http://www.qlcplus.org/Workspace";

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static Document parse(String xml) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    private static Element node(Document doc, String tag, int index) {
        return (Element) doc.getElementsByTagNameNS(NS, tag).item(index);
    }

    private static int attr(Element node, String name) {
        return Integer.parseInt(node.getAttribute(name));
    }

    public static void main(String[] args) throws Exception {
        var patches =
                List.of(
                        new StageNetwork.Patch(
                                new BlockPos(-300, 64, -200),
                                1,
                                "Stage & Wash",
                                "Left <A>",
                                FixtureProfile.RGB),
                        new StageNetwork.Patch(
                                new BlockPos(-299, 69, -196),
                                5,
                                "Stage & Wash",
                                "Right",
                                FixtureProfile.LASER),
                        new StageNetwork.Patch(
                                new BlockPos(-300, 65, -200),
                                11,
                                "Haze",
                                "Fog",
                                FixtureProfile.FOG));
        var files = ShowExport.files(patches);
        var doc = parse(files.get("venue.qxw"));
        check(
                doc.getElementsByTagNameNS(NS, "Fixture").getLength() == 3,
                "all fixtures must remain patched");
        check(
                doc.getElementsByTagNameNS(NS, "FixtureGroup").getLength() == 2,
                "named groups must be present");
        check(
                doc.getElementsByTagNameNS(NS, "FxItem").getLength() == 3,
                "every fixture needs a position");
        check(
                doc.getElementsByTagNameNS(NS, "Head").getLength() == 3,
                "all fixtures must remain in their groups");
        Element first = node(doc, "FxItem", 0),
                second = node(doc, "FxItem", 1),
                third = node(doc, "FxItem", 2);
        check(
                attr(first, "ID") == 0 && attr(second, "ID") == 1 && attr(third, "ID") == 2,
                "layout IDs must match fixtures");
        check(
                attr(second, "XPos") - attr(first, "XPos") == 1000,
                "one eastward block is one metre");
        check(attr(second, "YPos") - attr(first, "YPos") == 5000, "height follows Minecraft Y");
        check(attr(second, "ZPos") - attr(first, "ZPos") == 4000, "depth follows Minecraft Z");
        check(
                attr(third, "YPos") - attr(first, "YPos") == 1000,
                "vertically stacked pixels remain distinct");
        Element grid = node(doc, "Grid", 0);
        check(
                attr(grid, "Width") >= 5 && attr(grid, "Height") >= 7 && attr(grid, "Depth") >= 8,
                "grid contains the rig");
        check(attr(grid, "Units") == 0 && attr(grid, "POV") == 1, "metric top view");
        check(
                doc.getElementsByTagNameNS(NS, "Address").item(1).getTextContent().equals("4"),
                "DMX stays zero based");
        check(
                doc.getElementsByTagNameNS(NS, "Name").item(1).getTextContent().equals("Left <A>"),
                "name escapes roundtrip");
        check(
                files.get("patch.csv").contains("\"Stage & Wash\""),
                "CSV group and world data remain present");
        var empty = parse(ShowExport.files(List.of()).get("venue.qxw"));
        check(
                empty.getElementsByTagNameNS(NS, "FxItem").getLength() == 0,
                "empty venues have no phantom lights");
        if (args.length == 1) {
            var folder = java.nio.file.Path.of(args[0]);
            java.nio.file.Files.createDirectories(folder);
            for (var entry : files.entrySet())
                java.nio.file.Files.writeString(folder.resolve(entry.getKey()), entry.getValue());
        }
        System.out.println(
                "QLC export: spatial layout, groups, escaping, DMX and empty venue passed");
    }
}
