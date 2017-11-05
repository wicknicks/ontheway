package com.ontheway.core;

import com.ontheway.osm.generated.Fileformat.Blob;
import com.ontheway.osm.generated.Fileformat.BlobHeader;
import com.ontheway.osm.generated.Osmformat;
import com.ontheway.osm.generated.Osmformat.HeaderBlock;
import com.ontheway.osm.generated.Osmformat.PrimitiveBlock;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.spatial.SpatialStrategy;
import org.apache.lucene.spatial.prefix.RecursivePrefixTreeStrategy;
import org.apache.lucene.spatial.prefix.tree.GeohashPrefixTree;
import org.apache.lucene.spatial.query.SpatialArgs;
import org.apache.lucene.spatial.query.SpatialOperation;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.distance.DistanceUtils;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.InflaterInputStream;

public class Main {

    public static void main(String[] args) throws Exception {
        int items=0;
        Analyzer an = new StandardAnalyzer();

        SpatialContext ctx = SpatialContext.GEO;

        // stolen from https://github.com/apache/lucene-solr/blob/branch_7_1/lucene/spatial-extras/src/test/org/apache/lucene/spatial/SpatialExample.java
        SpatialStrategy strategy = new RecursivePrefixTreeStrategy(new GeohashPrefixTree(SpatialContext.GEO, 11), "myGeoField");

        Directory directory = new RAMDirectory();
        IndexWriter iwriter = new IndexWriter(directory, new IndexWriterConfig());

        // doc 1
        Document doc = new Document();
        String text = "This is the text to be indexed.";
        doc.add(new Field("description", text, TextField.TYPE_STORED));
        for (Field f: strategy.createIndexableFields(ctx.makePoint(-80.93, 33.77))) {
            doc.add(f);
        }
        doc.add(new Field("coordinates", -80.93 + " " + 33.77, TextField.TYPE_STORED));
        iwriter.addDocument(doc);

        // doc 2
        doc = new Document();
        text = "This is the prose to be indexed.";
        doc.add(new Field("description", text, TextField.TYPE_STORED));
        for (Field f: strategy.createIndexableFields(ctx.makePoint(50.25, 32.75))) {
            doc.add(f);
        }
        doc.add(new Field("coordinates", 50.25 + " " + 32.75, TextField.TYPE_STORED));
        iwriter.addDocument(doc);

        iwriter.close();

        DirectoryReader ireader = DirectoryReader.open(directory);
        IndexSearcher isearcher = new IndexSearcher(ireader);
        // Parse a simple query that searches for "text":
        QueryParser parser = new QueryParser("description", an);
        Query query = parser.parse("prose");
        ScoreDoc[] hits = isearcher.search(query, 10).scoreDocs;
        // Iterate through the results:
        for (int i = 0; i < hits.length; i++) {
            Document hitDoc = isearcher.doc(hits[i].doc);
            System.out.println("Result -> " + hitDoc.get("description") + " " + hitDoc.get("coordinates"));
        }

        SpatialArgs sargs = new SpatialArgs(SpatialOperation.Intersects,
                ctx.makeCircle(-80.0, 33.0, DistanceUtils.dist2Degrees(200, DistanceUtils.EARTH_MEAN_RADIUS_KM)));
        query = strategy.makeQuery(sargs);
        TopDocs docs = isearcher.search(query, 10, Sort.RELEVANCE);
        for (int i = 0; i < docs.scoreDocs.length; i++) {
            Document hitDoc = isearcher.doc(docs.scoreDocs[i].doc);
            System.out.println("(Geo) Result -> " + hitDoc.get("description") + " " + hitDoc.get("coordinates"));
        }

        if (docs.totalHits > 0) return;

        FileInputStream fis = new FileInputStream("/home/arjun/Downloads/california-latest.osm.pbf");
        DataInputStream dis = new DataInputStream(fis);

        for (; ; ) {
            if (dis.available() <= 0) break;

            int len = dis.readInt();
            byte[] blobHeader = new byte[len];
            dis.read(blobHeader);
            BlobHeader h = BlobHeader.parseFrom(blobHeader);
            byte[] blob = new byte[h.getDatasize()];
            dis.read(blob);
            Blob b = Blob.parseFrom(blob);

            InputStream blobData;
            if (b.hasZlibData()) {
                blobData = new InflaterInputStream(b.getZlibData().newInput());
            } else {
                blobData = b.getRaw().newInput();
            }
            // System.out.println("> " + h.getType());
            if (h.getType().equals("OSMHeader")) {
                HeaderBlock hb = HeaderBlock.parseFrom(blobData);
                //System.out.println("hb: " + hb.getSource());
            } else if (h.getType().equals("OSMData")) {
                PrimitiveBlock pb = PrimitiveBlock.parseFrom(blobData);
                for (Osmformat.PrimitiveGroup group : pb.getPrimitivegroupList()) {

                    if (!group.hasDense()) continue;

                    List<Coordinate> coordinates = new ArrayList<>();
                    long encodedLat = 0;
                    long encodedLon = 0;
                    for (int i=0; i<group.getDense().getLatList().size(); i++) {
                        encodedLat += group.getDense().getLatList().get(i);
                        encodedLon += group.getDense().getLonList().get(i);
                        double latitude = .000000001 * (pb.getLatOffset() + (pb.getGranularity() * encodedLat));
                        double longitude = .000000001 * (pb.getLonOffset() + (pb.getGranularity() * encodedLon));
                        coordinates.add(new Coordinate(latitude, longitude));
                    }

                    List<String> attrs = new ArrayList<>();
                    List<Integer> kvs = group.getDense().getKeysValsList();
                    String current = "";
                    for (int i=0; i<kvs.size(); i++) {
                        if (kvs.get(i) == 0) {
                            attrs.add(current);
                            current = "";
                        } else {
                            int k = kvs.get(i);
                            int v = kvs.get(i+1);
                            i++;
                            current += pb.getStringtable().getS(k).toStringUtf8()
                                    + "="
                                    + pb.getStringtable().getS(v).toStringUtf8()
                                    + "; ";

                        }
                    }


                    int pos = 0;
                    for (String attr: attrs) {
                        if (attr.toLowerCase().contains("in-n-out")) {
                            System.out.println("at pos=" + pos +", all attributes --> " + attr);
                            System.out.println("coordinates at pos=" + coordinates.get(pos));
                        }
                        pos++;
                    }

                    items += kvs.size();

                }
                //System.out.println("pb: " + pb.getGranularity());
            }
        }

        fis.close();


        System.out.println("Total of " + items + " items");
    }

}