package com.cozooo.dlc_manager.core;

import java.util.Locale;

public final class DlcVersionUtil {
    private static final String DEFAULT_VERSION = "1.0.0";

    public enum Channel {
        STABLE(0),
        BETA(1),
        ALPHA(2),
        NIGHTLY(3);

        private final int priority;

        Channel(int priority) {
            this.priority = priority;
        }

        public int priority() {
            return priority;
        }

        public boolean accepts(Channel candidate) {
            return candidate != null && candidate.priority <= priority;
        }
    }

    private DlcVersionUtil() {
    }

    public static Channel channelOf(String version) {
        ParsedVersion parsed = ParsedVersion.parse(version);
        if (!parsed.knownQualifier) {
            return Channel.STABLE;
        }
        return switch (parsed.qualifierRank) {
            case ParsedVersion.QUALIFIER_ALPHA -> Channel.ALPHA;
            case ParsedVersion.QUALIFIER_BETA, ParsedVersion.QUALIFIER_RC -> Channel.BETA;
            case ParsedVersion.QUALIFIER_BUILD -> Channel.NIGHTLY;
            default -> Channel.STABLE;
        };
    }

    public static Channel parseFilter(String value) {
        if (value == null || value.isBlank()) {
            return Channel.NIGHTLY;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "stable" -> Channel.STABLE;
            case "beta" -> Channel.BETA;
            case "alpha" -> Channel.ALPHA;
            case "nightly" -> Channel.NIGHTLY;
            default -> Channel.NIGHTLY;
        };
    }

    public static Channel channelByName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "stable" -> Channel.STABLE;
            case "beta" -> Channel.BETA;
            case "alpha" -> Channel.ALPHA;
            case "nightly" -> Channel.NIGHTLY;
            default -> null;
        };
    }

    public static boolean isWithinFilter(String version, Channel filter) {
        return filter == null || filter.accepts(channelOf(version));
    }

    public static int compare(String a, String b) {
        ParsedVersion left = ParsedVersion.parse(a);
        ParsedVersion right = ParsedVersion.parse(b);
        int max = Math.max(left.numericParts.length, right.numericParts.length);
        for (int i = 0; i < max; i++) {
            int l = i < left.numericParts.length ? left.numericParts[i] : 0;
            int r = i < right.numericParts.length ? right.numericParts[i] : 0;
            if (l != r) {
                return Integer.compare(l, r);
            }
        }
        if (left.knownQualifier && right.knownQualifier) {
            if (left.qualifierRank != right.qualifierRank) {
                return Integer.compare(left.qualifierRank, right.qualifierRank);
            }
            if (left.qualifierNumber != right.qualifierNumber) {
                return Integer.compare(left.qualifierNumber, right.qualifierNumber);
            }
            if (left.qualifierRank == ParsedVersion.QUALIFIER_BUILD) {
                return 0;
            }
            int qualifierTextCompare = left.qualifierText.compareToIgnoreCase(right.qualifierText);
            if (qualifierTextCompare != 0) {
                return qualifierTextCompare;
            }
            return left.normalized.compareToIgnoreCase(right.normalized);
        }
        if (left.knownQualifier != right.knownQualifier) {
            return left.knownQualifier ? 1 : -1;
        }
        return left.normalized.compareToIgnoreCase(right.normalized);
    }

    public static boolean isNewer(String currentVersion, String candidateVersion) {
        return compare(currentVersion, candidateVersion) < 0;
    }

    public static boolean allows(String currentVersion, String candidateVersion) {
        return channelOf(currentVersion).accepts(channelOf(candidateVersion));
    }

    private static String safeVersion(String value) {
        return value == null || value.isBlank() ? DEFAULT_VERSION : value.trim();
    }

    private static int parseVersionPart(String part) {
        if (part == null) {
            return 0;
        }
        String normalized = part.trim();
        int idx = normalized.indexOf('-');
        if (idx >= 0) {
            normalized = normalized.substring(0, idx);
        }
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static final class ParsedVersion {
        private static final int QUALIFIER_ALPHA = 0;
        private static final int QUALIFIER_BETA = 1;
        private static final int QUALIFIER_RC = 2;
        private static final int QUALIFIER_RELEASE = 3;
        private static final int QUALIFIER_HOTFIX = 4;
        private static final int QUALIFIER_BUILD = 5;

        private final String normalized;
        private final int[] numericParts;
        private final boolean knownQualifier;
        private final int qualifierRank;
        private final int qualifierNumber;
        private final String qualifierText;

        private ParsedVersion(String normalized, int[] numericParts, boolean knownQualifier, int qualifierRank, int qualifierNumber, String qualifierText) {
            this.normalized = normalized;
            this.numericParts = numericParts;
            this.knownQualifier = knownQualifier;
            this.qualifierRank = qualifierRank;
            this.qualifierNumber = qualifierNumber;
            this.qualifierText = qualifierText == null ? "" : qualifierText;
        }

        private static ParsedVersion parse(String value) {
            String normalized = safeVersion(value);
            int split = findSuffixStart(normalized);
            String core = split < 0 ? normalized : normalized.substring(0, split);
            String suffix = split < 0 ? "" : normalized.substring(split);
            int[] numericParts = parseNumericParts(core);
            Qualifier qualifier = parseQualifier(suffix);
            return new ParsedVersion(normalized, numericParts, qualifier.known, qualifier.rank, qualifier.number, qualifier.text);
        }

        private static int findSuffixStart(String value) {
            for (int i = 0; i < value.length(); i++) {
                char ch = value.charAt(i);
                if ((ch >= '0' && ch <= '9') || ch == '.') {
                    continue;
                }
                return i;
            }
            return -1;
        }

        private static int[] parseNumericParts(String core) {
            String[] parts = safeVersion(core).split("\\.");
            int[] parsed = new int[parts.length];
            for (int i = 0; i < parts.length; i++) {
                parsed[i] = parseVersionPart(parts[i]);
            }
            return parsed;
        }

        private static Qualifier parseQualifier(String suffix) {
            if (suffix == null || suffix.isBlank()) {
                return new Qualifier(true, QUALIFIER_RELEASE, 0, "release");
            }
            String normalized = suffix.trim();
            while (!normalized.isEmpty()) {
                char ch = normalized.charAt(0);
                if (ch == '-' || ch == '_' || ch == '.') {
                    normalized = normalized.substring(1);
                    continue;
                }
                break;
            }
            if (normalized.isBlank()) {
                return new Qualifier(true, QUALIFIER_RELEASE, 0, "release");
            }
            String lower = normalized.toLowerCase(Locale.ROOT);
            for (String name : new String[] {"alpha", "beta", "rc", "hotfix", "fix", "build", "nightly"}) {
                if (!lower.startsWith(name)) {
                    continue;
                }
                String rest = normalized.substring(name.length());
                int number = 0;
                if (!rest.isBlank()) {
                    rest = rest.trim();
                    while (!rest.isEmpty()) {
                        char ch = rest.charAt(0);
                        if (ch == '-' || ch == '_' || ch == '.') {
                            rest = rest.substring(1);
                            continue;
                        }
                        break;
                    }
                    if (!rest.isBlank()) {
                        StringBuilder digits = new StringBuilder();
                        int index = 0;
                        while (index < rest.length() && Character.isDigit(rest.charAt(index))) {
                            digits.append(rest.charAt(index));
                            index++;
                        }
                        if (digits.length() > 0) {
                            try {
                                number = Integer.parseInt(digits.toString());
                            } catch (NumberFormatException ignored) {
                                number = 0;
                            }
                        }
                    }
                }
                int rank = switch (name) {
                    case "alpha" -> QUALIFIER_ALPHA;
                    case "beta" -> QUALIFIER_BETA;
                    case "rc" -> QUALIFIER_RC;
                    case "hotfix", "fix" -> QUALIFIER_HOTFIX;
                    case "build", "nightly" -> QUALIFIER_BUILD;
                    default -> QUALIFIER_RELEASE;
                };
                String text = "fix".equals(name) ? "hotfix" : ("nightly".equals(name) ? "build" : name);
                return new Qualifier(true, rank, number, text);
            }
            return new Qualifier(false, QUALIFIER_RELEASE, 0, normalized);
        }
    }

    private static final class Qualifier {
        private final boolean known;
        private final int rank;
        private final int number;
        private final String text;

        private Qualifier(boolean known, int rank, int number, String text) {
            this.known = known;
            this.rank = rank;
            this.number = number;
            this.text = text == null ? "" : text;
        }
    }
}
