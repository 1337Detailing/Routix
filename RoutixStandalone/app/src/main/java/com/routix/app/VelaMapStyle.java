package com.routix.app;

import org.maplibre.android.maps.Style;
import org.maplibre.android.style.layers.BackgroundLayer;
import org.maplibre.android.style.layers.FillExtrusionLayer;
import org.maplibre.android.style.layers.FillLayer;
import org.maplibre.android.style.layers.Layer;
import org.maplibre.android.style.layers.LineLayer;
import org.maplibre.android.style.layers.SymbolLayer;
import org.maplibre.android.style.expressions.Expression;

import static org.maplibre.android.style.layers.PropertyFactory.*;

/**
 * Vela-inspired palette on the LIVE OpenFreeMap Liberty style.
 *
 * Vela (GPL-3.0, https://github.com/PimpinPumpkin/Vela) also starts from Liberty
 * and paints its own modern color palette, roads and buildings at runtime.
 * Keep the URL live: bundling Vela's Liberty JSON would pin OpenFreeMap's dated
 * vector tile snapshot and eventually produce a blank map.
 *
 * This is intentionally a basemap-only adapter. Routix owns GPS, routes, traces,
 * annotations and the offline maps and does not borrow Vela's navigation code.
 */
final class VelaMapStyle {
    static final String LIVE_LIBERTY = "https://tiles.openfreemap.org/styles/liberty";

    private VelaMapStyle() {}

    static void apply(Style style, boolean dark) {
        final String land = dark ? "#162640" : "#f8f7f7";
        final String water = dark ? "#000d2a" : "#90daee";
        final String green = dark ? "#0d3847" : "#d3f8e1";
        final String building = dark ? "#1c3b69" : "#e8e9ed";
        final String buildingBorder = dark ? "#2e3d6d" : "#d6d9e6";
        final String label = dark ? "#c3cad6" : "#3c4043";
        final String halo = dark ? "#1a2230" : "#ffffff";

        Layer background = style.getLayer("background");
        if (background instanceof BackgroundLayer)
            background.setProperties(backgroundColor(land));
        fill(style, water, "water");
        line(style, water, "waterway_river", "waterway_other");
        fill(style, green, "park", "landcover_grass", "landcover_wood");
        fill(style, dark ? "#0d4956" : "#a9eac2", "landuse_pitch", "landuse_track");
        fill(style, dark ? "#1c2638" : "#f0eded", "landuse_school");
        fill(style, dark ? "#1c2638" : "#fdf9ef", "landuse_hospital");

        // Remove Liberty's loud landuse blocks and patterned plazas/wetlands.
        for (Layer layer : style.getLayers()) {
            if (layer instanceof FillLayer
                    && (layer.getId().startsWith("landuse") || layer.getId().startsWith("landcover"))
                    && !isVegetation(layer.getId()) && !"landuse_school".equals(layer.getId())
                    && !"landuse_hospital".equals(layer.getId())) {
                layer.setProperties(fillColor(dark ? "#2a3546" : land), fillOpacity(dark ? .5f : 1f));
            }
            if (layer instanceof SymbolLayer)
                layer.setProperties(textColor(label), textHaloColor(halo),
                        textHaloWidth(layer.getId().startsWith("highway-name") ? 1.9f : 1.1f));
        }
        hide(style, "landcover_wetland", "road_area_pattern", "park_outline",
                "road_path_pedestrian", "bridge_path_pedestrian",
                "bridge_path_pedestrian_casing", "tunnel_path_pedestrian",
                "road_major_rail_hatching", "road_transit_rail_hatching",
                "bridge_major_rail_hatching", "bridge_transit_rail_hatching",
                "tunnel_major_rail_hatching", "tunnel_transit_rail_hatching");

        addFlatArea(style, "routix-vela-wetland", "landcover_wetland", "landcover",
                "wetland", dark ? "#0d3847" : "#d3f8e1");
        addFlatArea(style, "routix-vela-commercial", "park", "landuse",
                "commercial", dark ? "#1c2638" : "#fdf9ef");
        addFlatArea(style, "routix-vela-retail", "park", "landuse",
                "retail", dark ? "#1c2638" : "#fdf9ef");

        // Vela's Google-like road palette. Cover tunnels too; otherwise short
        // tunnel sections remain cream/yellow under the modern road network.
        line(style, dark ? "#476789" : "#8aa4c0",
                "road_motorway", "road_motorway_link", "bridge_motorway", "bridge_motorway_link",
                "tunnel_motorway", "tunnel_motorway_link");
        line(style, dark ? "#476789" : "#aab9c9",
                "road_trunk_primary", "bridge_trunk_primary", "tunnel_trunk_primary",
                "road_secondary_tertiary", "bridge_secondary_tertiary", "tunnel_secondary_tertiary",
                "road_minor", "bridge_street", "tunnel_minor",
                "road_link", "bridge_link", "tunnel_link");
        line(style, dark ? "#2a4056" : "#9bacbc",
                "road_service_track", "bridge_service_track", "tunnel_service_track");
        final String motorwayEdge = dark ? "#07090e" : "#5e75a0";
        final String streetEdge = dark ? "#07090e" : "#94a1af";
        line(style, motorwayEdge,
                "road_motorway_casing", "road_motorway_link_casing",
                "bridge_motorway_casing", "bridge_motorway_link_casing",
                "tunnel_motorway_casing", "tunnel_motorway_link_casing");
        line(style, streetEdge,
                "road_trunk_primary_casing", "road_secondary_tertiary_casing",
                "road_minor_casing", "road_link_casing",
                "bridge_trunk_primary_casing", "bridge_secondary_tertiary_casing",
                "bridge_street_casing", "bridge_link_casing",
                "tunnel_trunk_primary_casing", "tunnel_secondary_tertiary_casing",
                "tunnel_street_casing", "tunnel_link_casing");
        line(style, dark ? "#07090e" : "#8593a2",
                "road_service_track_casing", "bridge_service_track_casing", "tunnel_service_track_casing");

        Layer flat = style.getLayer("building");
        if (flat instanceof FillLayer) {
            flat.setProperties(fillColor(building), fillOutlineColor(buildingBorder));
            // Liberty's original flat building maxzoom is exclusive and can be 14.
            flat.setMinZoom(16f);
            flat.setMaxZoom(24f);
        }
        Layer extrusion = style.getLayer("building-3d");
        if (extrusion instanceof FillExtrusionLayer) {
            extrusion.setMinZoom(17f);
            extrusion.setProperties(fillExtrusionColor(building), fillExtrusionOpacity(1f));
        }
        line(style, dark ? "#2a4056" : "#abb5c5", "aeroway_runway", "aeroway_taxiway");
        fill(style, dark ? "#1c2638" : "#e8e9ed", "aeroway_fill");

        for (String id : new String[]{"highway-name-path", "highway-name-minor", "highway-name-major"}) {
            Layer layer = style.getLayer(id);
            if (layer instanceof SymbolLayer)
                layer.setProperties(textFont(new String[]{"Noto Sans Bold"}), textHaloWidth(1.9f));
        }
        // House numbers are present in OpenMapTiles but not enabled by Liberty.
        if (style.getLayer("routix-vela-house-numbers") == null && style.getSource("openmaptiles") != null) {
            SymbolLayer numbers = new SymbolLayer("routix-vela-house-numbers", "openmaptiles")
                    .withSourceLayer("housenumber")
                    .withProperties(textField(Expression.coalesce(Expression.get("housenumber"), Expression.literal(""))),
                            textFont(new String[]{"Noto Sans Regular"}), textSize(10f),
                            textColor(label), textHaloColor(halo), textHaloWidth(1f),
                            textAllowOverlap(false));
            numbers.setMinZoom(17f);
            style.addLayer(numbers);
        }
    }

    private static boolean isVegetation(String id) {
        return "park".equals(id) || "landcover_grass".equals(id)
                || "landcover_wood".equals(id) || "landuse_pitch".equals(id)
                || "landuse_track".equals(id);
    }

    private static void hide(Style style, String... ids) {
        for (String id : ids) {
            Layer layer = style.getLayer(id);
            if (layer != null) layer.setProperties(visibility("none"));
        }
    }

    private static void fill(Style style, String color, String... ids) {
        for (String id : ids) {
            Layer layer = style.getLayer(id);
            if (layer instanceof FillLayer) layer.setProperties(fillColor(color));
        }
    }

    private static void line(Style style, String color, String... ids) {
        for (String id : ids) {
            Layer layer = style.getLayer(id);
            if (layer instanceof LineLayer) layer.setProperties(lineColor(color));
        }
    }

    private static void addFlatArea(Style style, String id, String anchor, String sourceLayer,
                                     String kind, String color) {
        if (style.getLayer(id) != null || style.getSource("openmaptiles") == null
                || style.getLayer(anchor) == null) return;
        FillLayer layer = new FillLayer(id, "openmaptiles").withSourceLayer(sourceLayer)
                .withFilter(Expression.eq(Expression.get("class"), Expression.literal(kind)))
                .withProperties(fillColor(color), fillOpacity(1f));
        layer.setMinZoom(12f);
        style.addLayerAbove(layer, anchor);
    }
}
