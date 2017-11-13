package com.ontheway.core;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.ParseException;
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

import java.io.IOException;
import java.nio.file.FileSystems;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Intersector {

    final DirectoryReader dirReader;

    final SpatialContext ctx = SpatialContext.GEO;

    // stolen from
    // https://github.com/apache/lucene-solr/blob/branch_7_1/lucene/spatial-extras/
    // src/test/org/apache/lucene/spatial/SpatialExample.java
    final SpatialStrategy strategy = new RecursivePrefixTreeStrategy(
            new GeohashPrefixTree(SpatialContext.GEO, 11), "placeCoordinates");

    public Intersector(String indexLocation) throws IOException {
        Directory directory = new NIOFSDirectory(FileSystems.getDefault().getPath(indexLocation));
        dirReader = DirectoryReader.open(directory);
    }

    public Map<String, Document> searchWithLatLong(double lat, double lon) throws IOException {
        SpatialArgs sargs = new SpatialArgs(SpatialOperation.Intersects,
                ctx.getShapeFactory().circle(lon, lat,
                        DistanceUtils.dist2Degrees(Vars.PERIMETER, DistanceUtils.EARTH_MEAN_RADIUS_KM)));
        Query geoQuery = strategy.makeQuery(sargs);

        IndexSearcher isearcher = new IndexSearcher(dirReader);

        Map<String, Document> allDocuments = new HashMap<>();
        TopDocs docs = isearcher.search(geoQuery, 100, Sort.RELEVANCE);
        ScoreDoc last = null;
        for (int i = 0; i < docs.scoreDocs.length; i++) {
            Document hitDoc = isearcher.doc(docs.scoreDocs[i].doc);
            allDocuments.put(hitDoc.getField("id").stringValue(), hitDoc);
            last = docs.scoreDocs[i];
        }

        int remaining = (int) docs.totalHits;
        if (remaining == 0) return allDocuments;

        docs = isearcher.searchAfter(last, geoQuery, remaining);
        for (int i = 0; i < docs.scoreDocs.length; i++) {
            Document hitDoc = isearcher.doc(docs.scoreDocs[i].doc);
            allDocuments.put(hitDoc.getField("id").stringValue(), hitDoc);
        }

        System.out.println("Returning: " + allDocuments.size() + ", Total # of docs: " + docs.totalHits);
        return allDocuments;
    }

    public Map<String, Document> searchByKeyword(String keyword) throws ParseException, IOException {
        IndexSearcher isearcher = new IndexSearcher(dirReader);
        QueryParser parser = new QueryParser("description", new StandardAnalyzer());
        Query query = parser.parse(keyword);
        TopDocs docs = isearcher.search(query, 10);

        Map<String, Document> allDocuments = new HashMap<>();
        ScoreDoc last = null;
        for (int i = 0; i < docs.scoreDocs.length; i++) {
            Document hitDoc = isearcher.doc(docs.scoreDocs[i].doc);
            allDocuments.put(hitDoc.getField("id").stringValue(), hitDoc);
            last = docs.scoreDocs[i];
        }

        int remaining = (int) docs.totalHits;
        if (remaining == 0) {
            System.out.println("No (keyword) results for '" + keyword + "'");
            return allDocuments;
        }

        docs = isearcher.searchAfter(last, query, remaining);
        for (int i = 0; i < docs.scoreDocs.length; i++) {
            Document hitDoc = isearcher.doc(docs.scoreDocs[i].doc);
            allDocuments.put(hitDoc.getField("id").stringValue(), hitDoc);
        }

        System.out.println("Returning: " + allDocuments.size() + ", Total # of docs: " + docs.totalHits);
        return allDocuments;
    }

    public Map<String, Document> intersect(Map<String, Document> small, Map<String, Document> large) {
        Set<String> intersectionIds = large.keySet();
        intersectionIds.retainAll(small.keySet());

        Map<String, Document> intersectionMap = new HashMap<>();
        for (String id: intersectionIds) {
            intersectionMap.put(id, small.get(id));
        }

        System.out.println("Returning: " + intersectionMap.size());
        return intersectionMap;
    }

    public Map<String, Document> intersectingSearch(double lat, double lon, String keyword)
            throws IOException, ParseException {
        return intersect(searchWithLatLong(lat, lon), searchByKeyword(keyword));
    }
}
