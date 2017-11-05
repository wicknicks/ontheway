package com.ontheway.core;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.spatial.SpatialStrategy;
import org.apache.lucene.spatial.prefix.RecursivePrefixTreeStrategy;
import org.apache.lucene.spatial.prefix.tree.GeohashPrefixTree;
import org.apache.lucene.spatial.query.SpatialArgs;
import org.apache.lucene.spatial.query.SpatialOperation;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.NIOFSDirectory;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.distance.DistanceUtils;

import java.nio.file.FileSystems;
import java.util.Scanner;

public class OSMIndexSearcher {

    public static void main(String[] args) throws Exception {
        SpatialContext ctx = SpatialContext.GEO;

        // stolen from https://github.com/apache/lucene-solr/blob/branch_7_1/lucene/spatial-extras/src/test/org/apache/lucene/spatial/SpatialExample.java
        SpatialStrategy strategy = new RecursivePrefixTreeStrategy(new GeohashPrefixTree(SpatialContext.GEO, 11), "placeCoordinates");

        SpatialArgs sargs = new SpatialArgs(SpatialOperation.Intersects,
                ctx.getShapeFactory().circle(-121.5592, 44.297694, DistanceUtils.dist2Degrees(20, DistanceUtils.EARTH_MEAN_RADIUS_KM)));
        Query geoQuery = strategy.makeQuery(sargs);

//        SpatialArgs args2 = new SpatialArgsParser().parse("Intersects(BUFFER(POINT(-121.5592 44.297694),0.1798640735523327))", ctx);
//        query = strategy.makeQuery(args2);

        Directory directory = new NIOFSDirectory(FileSystems.getDefault().getPath("/home/arjun/Sandbox/ontheway/indices/first/"));
        DirectoryReader ireader = DirectoryReader.open(directory);
        IndexSearcher isearcher = new IndexSearcher(ireader);

        TopDocs docs = isearcher.search(geoQuery, 100, Sort.RELEVANCE);
        for (int i = 0; i < docs.scoreDocs.length; i++) {
            Document hitDoc = isearcher.doc(docs.scoreDocs[i].doc);
            System.out.println("(Geo) Result -> "
                    + hitDoc.get("id") + " "
                    + hitDoc.get("description") + " "
                    + hitDoc.get("coordinates_text"));
        }

        System.out.println("Search for different places ( :q to quit )");

        while (true) {

            System.out.print("> ");
            Scanner scanner = new Scanner(System.in);
            String line = scanner.nextLine();
            if (line.equals(":q")) break;

            QueryParser parser = new QueryParser("description", new StandardAnalyzer());
            Query query = parser.parse(line);
            docs = isearcher.search(query, 10);
            System.out.println("Total hits= " + docs.totalHits);
            for (int i = 0; i < docs.scoreDocs.length; i++) {
                Document hitDoc = isearcher.doc(docs.scoreDocs[i].doc);
                System.out.println("(Text) Result -> "
                        + hitDoc.get("id") + " "
                        + hitDoc.get("description") + " "
                        + hitDoc.get("coordinates_text"));
            }

            System.out.println();
        }

    }
}
