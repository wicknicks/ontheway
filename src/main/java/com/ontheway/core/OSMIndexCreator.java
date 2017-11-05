package com.ontheway.core;

import com.ontheway.osm.generated.Fileformat;
import com.ontheway.osm.generated.Osmformat;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.spatial.SpatialStrategy;
import org.apache.lucene.spatial.prefix.RecursivePrefixTreeStrategy;
import org.apache.lucene.spatial.prefix.tree.GeohashPrefixTree;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.NIOFSDirectory;
import org.locationtech.spatial4j.context.SpatialContext;

import java.io.*;
import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.InflaterInputStream;

public class OSMIndexCreator implements Closeable {

    private final File file;
    private final IndexWriter indexWriter;

    private final SpatialContext ctx = SpatialContext.GEO;
    private final SpatialStrategy strategy = new RecursivePrefixTreeStrategy(
            new GeohashPrefixTree(SpatialContext.GEO, 11), "placeCoordinates");

    public OSMIndexCreator(String pathToFile) throws IOException {
        File file = new File(pathToFile);
        if (!file.exists()) throw new FileNotFoundException(pathToFile);
        this.file = file;

        // index writer setup
        Directory directory = new NIOFSDirectory(FileSystems.getDefault().getPath("/home/arjun/Sandbox/ontheway/indices/first/"));
        this.indexWriter = new IndexWriter(directory, new IndexWriterConfig());
    }

    public void create() throws IOException {
        try (DataInputStream dis = new DataInputStream(new FileInputStream(file))) {
            for (; ; ) {
                if (dis.available() <= 0) break;
                if (dis.available() % 2 == 0) System.out.println("Still to read: " + dis.available() + " bytes.");

                int len = dis.readInt();
                byte[] blobHeader = new byte[len];
                dis.read(blobHeader);
                Fileformat.BlobHeader h = Fileformat.BlobHeader.parseFrom(blobHeader);
                byte[] blob = new byte[h.getDatasize()];
                dis.read(blob);
                Fileformat.Blob b = Fileformat.Blob.parseFrom(blob);

                InputStream blobData;
                if (b.hasZlibData()) {
                    blobData = new InflaterInputStream(b.getZlibData().newInput());
                } else {
                    blobData = b.getRaw().newInput();
                }
                // System.out.println("> " + h.getType());
                if (h.getType().equals("OSMHeader")) {
                    Osmformat.HeaderBlock hb = Osmformat.HeaderBlock.parseFrom(blobData);
                    //System.out.println("hb: " + hb.getSource());
                } else if (h.getType().equals("OSMData")) {
                    Osmformat.PrimitiveBlock pb = Osmformat.PrimitiveBlock.parseFrom(blobData);
                    processPrimitive(pb);
                }
            }
        }
    }

    public void processPrimitive(Osmformat.PrimitiveBlock pb) throws IOException {
        for (Osmformat.PrimitiveGroup group : pb.getPrimitivegroupList()) {
            if (group.hasDense()) processDenseNode(pb, group.getDense());
        }
    }

    private void processDenseNode(Osmformat.PrimitiveBlock pb, Osmformat.DenseNodes dense) throws IOException {
        List<Coordinate> coordinates = new ArrayList<>();
        long encodedLat = 0;
        long encodedLon = 0;
        for (int i=0; i<dense.getLatList().size(); i++) {
            encodedLat += dense.getLatList().get(i);
            encodedLon += dense.getLonList().get(i);
            double latitude = .000000001 * (pb.getLatOffset() + (pb.getGranularity() * encodedLat));
            double longitude = .000000001 * (pb.getLonOffset() + (pb.getGranularity() * encodedLon));
            coordinates.add(new Coordinate(latitude, longitude));
        }

        List<String> attrs = new ArrayList<>();
        List<Integer> kvs = dense.getKeysValsList();
        StringBuilder current = new StringBuilder();
        for (int i=0; i<kvs.size(); i++) {
            if (kvs.get(i) == 0) {
                attrs.add(current.toString());
                current = new StringBuilder();
            } else {
                int k = kvs.get(i);
                int v = kvs.get(i+1);
                i++;
                current.append(pb.getStringtable().getS(k).toStringUtf8())
                        .append("=")
                        .append(pb.getStringtable().getS(v).toStringUtf8())
                        .append("; ");

            }
        }

        addToIndex(dense.getIdList(), coordinates, attrs);
    }

    private void addToIndex(List<Long> idList, List<Coordinate> coordinates, List<String> attrs) throws IOException {
        long currentId = 0;
        for (int i=0; i<coordinates.size(); i++) {
            currentId += idList.get(i);
            String attr = attrs.get(i);
            if (attrs.get(i).length() > 0) {
                if (attr.toLowerCase().contains("best western")) {
                    System.out.println(attr + " " + coordinates.get(i));
                }
                writeToIndex(currentId, coordinates.get(i), attrs.get(i));
            }
        }
    }

    private void writeToIndex(Long id, Coordinate coordinate, String attr) throws IOException {
        Document doc = new Document();
        doc.add(new Field("id", String.valueOf(id), TextField.TYPE_STORED));
        doc.add(new Field("description", attr, TextField.TYPE_STORED));
        for (Field f: strategy.createIndexableFields(ctx.getShapeFactory().pointXY(coordinate.longitude, coordinate.latitude))) {
            doc.add(f);
        }
        doc.add(new Field("coordinates_text", String.valueOf(coordinate), TextField.TYPE_STORED));
        indexWriter.addDocument(doc);
    }

    public void close() throws IOException {
        indexWriter.close();
    }

    public static void main(String[] args) throws Exception {

        try (OSMIndexCreator c=new OSMIndexCreator("/home/arjun/Downloads/oregon-latest.osm.pbf")) {
            c.create();
        }
    }

}
