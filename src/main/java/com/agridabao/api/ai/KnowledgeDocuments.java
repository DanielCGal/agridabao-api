package com.agridabao.api.ai;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class KnowledgeDocuments {

    record KnowledgeDocument(String title, String text, String hash, List<String> references) {
    }

    private enum Mode { PLAIN, PESTS, GROWTH, GUIDE }

    private static final Map<String, String> CROPS = new LinkedHashMap<>();

    static {
        CROPS.put("TOMATO", "Tomato");
        CROPS.put("SQUASH", "Squash");
        CROPS.put("EGGPLANT", "Eggplant");
        CROPS.put("STRAWBERRY", "Strawberry");
        CROPS.put("BANANA", "Banana");
        CROPS.put("CACAO", "Cacao");
        CROPS.put("CORN", "Corn");
        CROPS.put("MAIS", "Corn");
        CROPS.put("MAIZE", "Corn");
        CROPS.put("DURIAN", "Durian");
        CROPS.put("MANGOSTEEN", "Mangosteen");
        CROPS.put("COCONUT", "Coconut");
        CROPS.put("MANGO", "Mango");
        CROPS.put("PINEAPPLE", "Pineapple");
        CROPS.put("PINEAAPLE", "Pineapple");
        CROPS.put("POMELO", "Pomelo");
        CROPS.put("PUMMELO", "Pomelo");
    }

    private static final Pattern GUIDE_TITLE =
            Pattern.compile("^([A-Z][A-Z ()/]{2,40}?) PRODUCTION( NOTES| GUIDE)?$");
    private static final Pattern NUMBERED_HEADING =
            Pattern.compile("^\\d{1,2}\\.\\s+[A-Z0-9][A-Z0-9 ,/&()'\\-\\u2013:]{2,90}$");
    private static final Pattern REFERENCES_LINE =
            Pattern.compile("^(REFERENCES?|References?)\\s*:?$");
    private static final Pattern SOURCE_LINE = Pattern.compile("^Source:\\s*(.+)$");
    private static final Pattern URL = Pattern.compile("https?://\\S+");
    private static final Pattern PERSON = Pattern.compile("^([A-Z][\\p{L}'\\-]+), [A-Z]\\b");
    private static final Pattern PAGE_NOTE = Pattern.compile("\\s*\\((pp?|pg)\\.?\\s.*$");

    private static final Set<String> PEST_TABLE_HEADERS = Set.of(
            "when they commonly appear",
            "possible trigger / cause of appearance",
            "mitigation / control methods",
            "crops:");

    private static final String PEST_LEGEND =
            "Each entry below names a pest or disease and then gives, in this order: when it "
                    + "commonly appears, the possible trigger or cause, and the mitigation or "
                    + "control methods.";

    private static final int FALLBACK_PART_CHARS = 60_000;
    private static final int MIN_DOCUMENT_CHARS = 200;
    private static final int MAX_REFERENCES = 3;

    private KnowledgeDocuments() {
    }

    static List<KnowledgeDocument> split(String export) {
        String[] lines = normalise(export).split("\n", -1);
        List<Draft> drafts = new ArrayList<>();

        Mode mode = Mode.PLAIN;
        Draft current = null;
        String crop = null;
        String pestKind = "pest";

        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            String line = raw.strip();
            String bare = stripQuotes(line);
            String lower = bare.toLowerCase(Locale.ROOT);

            if (lower.startsWith("davao climate / weather calendar")) {
                current = start(drafts, "Davao climate and weather calendar");
                mode = Mode.PLAIN;
                continue;
            }
            if (lower.startsWith("crops pest & disease davao city")) {
                mode = Mode.PESTS;
                current = null;
                continue;
            }
            if (lower.startsWith("agricultural crop production notes: davao city")) {
                current = start(drafts, "Davao City crop production notes (CLUP Volume 3)");
                mode = Mode.PLAIN;
                continue;
            }
            if (lower.startsWith("agricultural production, davao city")) {
                current = start(drafts, "Davao City agricultural production 2020-2024");
                mode = Mode.PLAIN;
                continue;
            }
            if (lower.startsWith("climate events and recommended mitigation measures")) {
                current = start(drafts, "Climate events and mitigation measures by month and crop");
                mode = Mode.GROWTH;
                continue;
            }

            Matcher guide = GUIDE_TITLE.matcher(bare);
            if (guide.matches()) {
                String guideCrop = cropIn(guide.group(1));
                if (guideCrop != null) {
                    crop = guideCrop;
                    current = start(drafts, crop + " production guide");
                    mode = Mode.GUIDE;
                    continue;
                }
            }

            if ((mode == Mode.PESTS || mode == Mode.GROWTH) && CROPS.containsKey(bare)) {
                crop = CROPS.get(bare);
                if (mode == Mode.PESTS) {
                    current = start(drafts, crop + " pests and diseases");
                    current.body.append(PEST_LEGEND).append("\n\n");
                    pestKind = "pest";
                } else {
                    current = start(drafts, crop + " growth cycle and planting steps");
                }
                continue;
            }

            if (current == null) {
                continue;
            }

            if (mode == Mode.PESTS) {
                if (lower.equals("pest:") || lower.equals("pests:")) {
                    pestKind = "pest";
                    continue;
                }
                if (lower.equals("disease:") || lower.equals("diseases:")) {
                    pestKind = "disease";
                    continue;
                }
                if (PEST_TABLE_HEADERS.contains(lower)) {
                    continue;
                }
                if (isPestRowName(raw, line, lines, i)) {
                    current.body.append("\n### ").append(crop).append(' ').append(pestKind)
                            .append(": ").append(line).append('\n');
                    continue;
                }
            }

            if (mode == Mode.GUIDE && NUMBERED_HEADING.matcher(line).matches()) {
                current.body.append("\n## ").append(crop).append(": ").append(line).append('\n');
                continue;
            }

            if (REFERENCES_LINE.matcher(line).matches()) {
                current.inReferences = true;
                current.body.append("\n## References for ").append(current.title).append('\n');
                continue;
            }

            Matcher source = SOURCE_LINE.matcher(line);
            if (source.matches() && current.references.size() < MAX_REFERENCES) {
                current.references.add(shorten(PAGE_NOTE.matcher(source.group(1)).replaceFirst("")));
            } else if (current.inReferences) {
                String reference = referenceName(line);
                if (reference != null && current.references.size() < MAX_REFERENCES) {
                    current.references.add(reference);
                }
            }

            current.body.append(line).append('\n');
        }

        List<KnowledgeDocument> documents = new ArrayList<>();
        Set<String> titles = new LinkedHashSet<>();
        for (Draft draft : drafts) {
            String body = collapseBlankLines(draft.body.toString()).strip();
            if (body.length() < MIN_DOCUMENT_CHARS || !titles.add(draft.title)) {
                continue;
            }
            String text = "# " + draft.title + "\n"
                    + "Part of the AgriDabaw-3D field guide for farming in Davao City, Philippines.\n\n"
                    + body + "\n";
            documents.add(new KnowledgeDocument(
                    draft.title, text, sha256(text), List.copyOf(draft.references)));
        }

        return documents.size() >= 3 ? documents : fallback(export);
    }

    static String contentId(List<KnowledgeDocument> documents) {
        StringBuilder all = new StringBuilder();
        for (KnowledgeDocument document : documents) {
            all.append(document.title()).append('\n').append(document.hash()).append('\n');
        }
        return sha256(all.toString()).substring(0, 12);
    }

    private static List<KnowledgeDocument> fallback(String export) {
        String text = collapseBlankLines(normalise(export)).strip();
        List<KnowledgeDocument> parts = new ArrayList<>();

        for (int start = 0, part = 1; start < text.length(); part++) {
            int end = Math.min(text.length(), start + FALLBACK_PART_CHARS);
            if (end < text.length()) {
                int breakAt = text.lastIndexOf('\n', end);
                if (breakAt > start + FALLBACK_PART_CHARS / 2) {
                    end = breakAt;
                }
            }
            String title = "AgriDabaw field guide, part " + part;
            String body = "# " + title + "\n\n" + text.substring(start, end).strip() + "\n";
            if (body.length() >= MIN_DOCUMENT_CHARS) {
                parts.add(new KnowledgeDocument(title, body, sha256(body), List.of()));
            }
            start = end;
        }

        return parts;
    }

    private static boolean isPestRowName(String raw, String line, String[] lines, int index) {
        if (line.isEmpty() || line.length() > 80 || raw.startsWith("\t") || isBullet(line)
                || line.endsWith(".") || line.endsWith(":")) {
            return false;
        }
        for (int next = index + 1; next < lines.length && next <= index + 8; next++) {
            if (!lines[next].isBlank()) {
                return lines[next].startsWith("\t");
            }
        }
        return false;
    }

    private static boolean isBullet(String line) {
        char first = line.charAt(0);
        return first == '•' || first == '*' || first == '-' || first == '●' || first == '○';
    }

    private static String cropIn(String heading) {
        for (String word : heading.split("[^A-Z]+")) {
            String crop = CROPS.get(word);
            if (crop != null) {
                return crop;
            }
        }
        return null;
    }

    private static String referenceName(String line) {
        if (line.length() < 8) {
            return null;
        }

        int year = line.indexOf(" (");
        if (year > 3) {
            String author = line.substring(0, year);
            Matcher person = PERSON.matcher(author);
            if (person.find()) {
                return author.contains("&") || author.indexOf(',') != author.lastIndexOf(',')
                        ? person.group(1) + " et al."
                        : person.group(1);
            }
            return shorten(author);
        }

        Matcher url = URL.matcher(line);
        if (url.find()) {
            try {
                String host = URI.create(url.group()).getHost();
                return host == null ? null : host.replaceFirst("^www\\.", "");
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }

        return line.length() <= 90 ? shorten(line) : null;
    }

    private static String shorten(String value) {
        String cleaned = value.strip().replaceAll("[.,;:]+$", "");
        return cleaned.length() <= 90 ? cleaned : cleaned.substring(0, 87).strip() + "...";
    }

    private static Draft start(List<Draft> drafts, String title) {
        for (Draft existing : drafts) {
            if (existing.title.equals(title)) {
                existing.inReferences = false;
                return existing;
            }
        }
        Draft draft = new Draft(title);
        drafts.add(draft);
        return draft;
    }

    private static String stripQuotes(String line) {
        return line.replaceAll("^[\"\\u201C\\u201D]+|[\"\\u201C\\u201D]+$", "").strip();
    }

    private static String normalise(String text) {
        String value = text == null ? "" : text;
        if (!value.isEmpty() && value.charAt(0) == '﻿') {
            value = value.substring(1);
        }
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static String collapseBlankLines(String text) {
        return text.replaceAll("[ \\t]+\\n", "\n").replaceAll("\\n{3,}", "\n\n");
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static final class Draft {
        final String title;
        final StringBuilder body = new StringBuilder();
        final Set<String> references = new LinkedHashSet<>();
        boolean inReferences;

        Draft(String title) {
            this.title = title;
        }
    }
}
