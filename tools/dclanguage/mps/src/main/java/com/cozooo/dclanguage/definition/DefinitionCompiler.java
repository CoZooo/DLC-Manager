package com.cozooo.dclanguage.definition;

import static com.cozooo.dclanguage.definition.DefinitionConcepts.*;
import com.google.gson.*;
import org.jetbrains.mps.openapi.model.SNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.io.IOException;
import java.security.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

public final class DefinitionCompiler {
    public static final String JAVA_PATH = "java/com/cozooo/dclanguage/generated/DcGeneratedSyntax.java";
    public static final String METADATA_PATH = "resources/com/cozooo/dclanguage/language-definition.json";
    private static final Gson JSON = new GsonBuilder().disableHtmlEscaping().serializeNulls().setPrettyPrinting().create();
    private static final Gson COMPACT = new GsonBuilder().disableHtmlEscaping().serializeNulls().create();
    private static final Set<String> REQUIRED_TOKENS = Set.of("assignment","categoryOpen","categoryClose","categorySeparator","block","arrayOpen","arrayClose","objectOpen","objectClose","itemSeparator","objectSeparator","escape","comment","lineComment","unicodeEscape");
    private static final Set<String> BOOLEAN_POLICIES = Set.of("allowBareValues", "allowEmptyAssignment", "allowTrailingComma", "allowNewlineSeparator", "caseInsensitiveBooleans", "allowDottedCategories", "allowColonCategories", "allowBareEntries", "preserveUnknownEscapes", "allowBom");
    private static final Set<String> INTEGER_POLICIES = Set.of("maxDepth", "indentWidth");
    private static final Set<String> CONSTRAINTS = Set.of("min", "max", "minLength", "maxLength", "pattern");
    private static final Pattern CONSTRAINT_NUMBER = Pattern.compile("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?");
    private DefinitionCompiler() { }
    public static List<String> validate(SNode definition) {
        try { metadata(definition, "validation");return List.of(); }
        catch (IllegalArgumentException exception) { return List.of(exception.getMessage()); }
    }
    public static JsonObject metadata(SNode definition, String hash) {
        if (!definition.getConcept().equals(concept("Definition"))) throw invalid("Expected a language Definition root");
        JsonObject result = new JsonObject();result.addProperty("formatVersion",integer(get(definition,"formatVersion"),"formatVersion"));result.addProperty("language",name(definition));
        JsonObject syntax = new JsonObject();JsonObject tokens = namedValues(definition,"tokens");
        if (!tokens.keySet().containsAll(REQUIRED_TOKENS)) throw invalid("Missing syntax tokens: " + difference(REQUIRED_TOKENS,tokens.keySet()));
        for (var entry : tokens.entrySet()) {
            if(!entry.getKey().matches("[a-z][A-Za-z0-9]*"))throw invalid("Invalid token name: "+entry.getKey());
            String value=entry.getValue().getAsString();
            if(value.isBlank() || value.chars().anyMatch(Character::isWhitespace))throw invalid("Empty or whitespace token: "+entry.getKey());
        }
        for (String group : List.of("category","array","object")) if (tokens.get(group+"Open").equals(tokens.get(group+"Close"))) throw invalid("Opening and closing delimiters conflict: "+group);
        for (String key : List.of("categoryOpen","categoryClose","arrayOpen","arrayClose","objectOpen","objectClose","comment","lineComment","itemSeparator"))
            if (tokens.get("assignment").equals(tokens.get(key))) throw invalid("Assignment token conflicts with "+key);
        if (tokens.get("comment").equals(tokens.get("lineComment"))) throw invalid("Comment tokens must be distinct");
        syntax.add("tokens",tokens);
        JsonArray strings=new JsonArray();Set<String> delimiters=new HashSet<>();Set<String> stringNames=new HashSet<>();
        for (SNode rule:children(definition,"strings")) {
            JsonObject item=new JsonObject();String delimiter=get(rule,"delimiter");
            if (delimiter.isEmpty() || delimiter.chars().anyMatch(Character::isWhitespace) || !delimiters.add(delimiter)) throw invalid("Empty or duplicate string delimiter");
            String ruleName=name(rule);if (!stringNames.add(ruleName)) throw invalid("Duplicate string rule: "+ruleName);
            for(String token:REQUIRED_TOKENS) if(tokens.get(token).getAsString().equals(delimiter)) throw invalid("String delimiter conflicts with "+token);
            item.addProperty("name",ruleName);item.addProperty("delimiter",delimiter);item.addProperty("multiline",bool(rule,"multiline"));item.addProperty("escaped",bool(rule,"escaped"));strings.add(item);
        }
        if(strings.isEmpty())throw invalid("A language needs string rules");syntax.add("stringRules",strings);
        JsonObject escapes=namedValues(definition,"escapes");
        for(var entry:List.copyOf(escapes.entrySet())) {
            if(entry.getKey().codePointCount(0,entry.getKey().length())!=1)throw invalid("An escape name must be one character");
            String code=entry.getValue().getAsString();if(!code.matches("U\\+[0-9A-Fa-f]{4,6}"))throw invalid("Escape values use U+XXXX code points");
            int value=Integer.parseInt(code.substring(2),16);if(!Character.isValidCodePoint(value))throw invalid("Invalid Unicode escape value");escapes.addProperty(entry.getKey(),new String(Character.toChars(value)));
        }
        syntax.add("escapes",escapes);
        JsonArray types=new JsonArray();Set<String> typeNames=new HashSet<>();for(SNode type:children(definition,"valueTypes")){String value=name(type);if(!typeNames.add(value))throw invalid("Duplicate value type: "+value);types.add(value);}if(types.isEmpty())throw invalid("Missing value types");syntax.add("valueTypes",types);
        JsonObject literals=namedValues(definition,"literals");for(String key:List.of("true","false","null"))if(!literals.has(key)||literals.get(key).getAsString().isEmpty())throw invalid("Missing literal: "+key);
        if(literals.entrySet().stream().map(e->e.getValue().getAsString()).distinct().count()!=literals.size())throw invalid("Literal spellings must be distinct");syntax.add("literals",literals);
        syntax.addProperty("numberPattern",singlePattern(definition,"numbers"));syntax.addProperty("bareKeyPattern",singlePattern(definition,"names"));result.add("syntax",syntax);
        JsonObject policies=validatedPolicies(definition);
        validateLiterals(literals,tokens,delimiters,syntax.get("numberPattern").getAsString(),policies.get("caseInsensitiveBooleans").getAsBoolean());
        result.add("policies",policies);
        JsonArray profiles=new JsonArray();Set<String> profileNames=new HashSet<>();for(SNode profile:children(definition,"profiles")) {
            JsonObject item=new JsonObject();String id=name(profile);if(!profileNames.add(id))throw invalid("Duplicate schema profile: "+id);item.addProperty("id",id);item.addProperty("description",get(profile,"description"));item.add("filePatterns",stringValues(profile,"filePatterns"));
            JsonArray categories=new JsonArray();Set<List<String>> paths=new HashSet<>();for(SNode category:children(profile,"categories"))category(category,List.of(),categories,paths,typeNames);item.add("categories",categories);profiles.add(item);
        }
        result.add("profiles",profiles);result.addProperty("contentHash",sha256(COMPACT.toJson(result).getBytes(StandardCharsets.UTF_8)));result.addProperty("definitionHash",hash);return result;
    }
    private static void category(SNode node,List<String> prefix,JsonArray output,Set<List<String>> paths,Set<String> types) {
        List<String> path=new ArrayList<>(prefix);String name=name(node);if(!name.equals("@root"))path.add(name);else if(!prefix.isEmpty())throw invalid("Root category cannot be nested");
        if(!paths.add(List.copyOf(path)))throw invalid("Duplicate category path: "+path);JsonObject item=new JsonObject();item.add("path",COMPACT.toJsonTree(path));item.addProperty("name",name.equals("@root")?"":name);item.addProperty("required",bool(node,"required"));item.addProperty("description",get(node,"description"));
        JsonArray fields=new JsonArray();Set<String> names=new HashSet<>();for(SNode field:children(node,"fields")) {
            String fieldName=name(field);if(!names.add(fieldName))throw invalid("Duplicate field in "+path+": "+fieldName);String type=get(field,"type");for(String alternative:type.split("\\|"))if(!types.contains(alternative))throw invalid("Unknown field type: "+type);
            JsonObject value=new JsonObject();value.addProperty("name",fieldName);value.addProperty("type",type);value.addProperty("required",bool(field,"required"));value.addProperty("description",get(field,"description"));value.add("enumValues",stringValues(field,"enumValues"));
            JsonObject constraints=validatedConstraints(field,type);
            value.add("constraints",constraints);String defaultValue=get(field,"defaultValue");if(!defaultValue.isEmpty())try{value.add("defaultValue",JsonParser.parseString(defaultValue));}catch(JsonParseException e){throw invalid("Invalid default value for "+fieldName);}fields.add(value);
        }
        item.add("fields",fields);output.add(item);for(SNode child:children(node,"categories"))category(child,path,output,paths,types);
    }
    private static JsonObject validatedPolicies(SNode definition) {
        JsonObject policies = namedValues(definition, "policies");
        for (String key : BOOLEAN_POLICIES) {
            if (!policies.has(key)) throw invalid("Missing boolean policy: " + key);
            String value = policies.get(key).getAsString();
            if (!value.equals("true") && !value.equals("false")) throw invalid("Boolean policy must be true or false: " + key);
        }
        for (String key : INTEGER_POLICIES) {
            if (!policies.has(key)) throw invalid("Missing integer policy: " + key);
            String value = policies.get(key).getAsString();
            if (!value.matches("[0-9]+") || integer(value, key) < 1) throw invalid("Positive integer policy required: " + key);
        }
        for (var entry : List.copyOf(policies.entrySet())) {
            String value = entry.getValue().getAsString();
            if (value.equals("true") || value.equals("false")) policies.addProperty(entry.getKey(), Boolean.parseBoolean(value));
            else if (value.matches("[0-9]+")) policies.addProperty(entry.getKey(), integer(value, entry.getKey()));
            else throw invalid("Policies must be booleans or positive integers: " + entry.getKey());
        }
        return policies;
    }
    private static void validateLiterals(JsonObject literals, JsonObject tokens, Set<String> delimiters, String numberPattern, boolean ignoreCase) {
        List<String> reserved = new ArrayList<>(delimiters);
        for (String key : List.of("comment", "lineComment", "categoryOpen", "categoryClose", "arrayOpen", "arrayClose", "objectOpen", "objectClose", "itemSeparator", "assignment", "objectSeparator", "block")) {
            reserved.add(tokens.get(key).getAsString());
        }
        List<String> seen = new ArrayList<>();
        for (var entry : literals.entrySet()) {
            String value = entry.getValue().getAsString();
            if (value.isBlank() || value.chars().anyMatch(Character::isWhitespace)) throw invalid("Literal contains whitespace: " + entry.getKey());
            for (String separator : reserved) if (value.contains(separator)) throw invalid("Literal conflicts with reserved syntax: " + entry.getKey());
            if (Pattern.matches(numberPattern, value)) throw invalid("Literal conflicts with the number rule: " + entry.getKey());
            for (String previous : seen) {
                if (ignoreCase ? previous.equalsIgnoreCase(value) : previous.equals(value)) throw invalid("Literal spellings must be distinct under the boolean case policy");
            }
            seen.add(value);
        }
    }
    private static JsonObject validatedConstraints(SNode field, String type) {
        JsonObject constraints = new JsonObject();
        Set<String> types = Set.of(type.split("\\|"));
        for (SNode constraint : children(field, "constraints")) {
            String key = name(constraint);
            if (!CONSTRAINTS.contains(key)) throw invalid("Unsupported constraint: " + key);
            if (constraints.has(key)) throw invalid("Duplicate constraint: " + key);
            boolean numeric = key.equals("min") || key.equals("max");
            boolean applicable = types.contains("any") || (numeric ? types.contains("number") || types.contains("integer") : types.contains("string"));
            if (!applicable) throw invalid("Constraint " + key + " is not supported for field type " + type);
            String expression = get(constraint, "value");
            if (key.equals("pattern")) {
                Pattern.compile(expression);
                constraints.addProperty(key, expression);
            } else {
                if (!CONSTRAINT_NUMBER.matcher(expression).matches()) throw invalid("Constraint must be a number: " + key);
                BigDecimal number;
                try { number = new BigDecimal(expression); }
                catch (NumberFormatException exception) { throw invalid("Constraint number is out of range: " + key); }
                if (!numeric) {
                    try { if (number.intValueExact() < 0) throw invalid("Length constraint must be non-negative: " + key); }
                    catch (ArithmeticException exception) { throw invalid("Length constraint must be a non-negative 32-bit integer: " + key); }
                }
                constraints.add(key, new JsonPrimitive(number));
            }
        }
        validateRange(constraints, "min", "max");
        validateRange(constraints, "minLength", "maxLength");
        return constraints;
    }
    private static void validateRange(JsonObject constraints, String lower, String upper) {
        if (constraints.has(lower) && constraints.has(upper) && constraints.get(lower).getAsBigDecimal().compareTo(constraints.get(upper).getAsBigDecimal()) > 0) {
            throw invalid("Constraint " + lower + " must not exceed " + upper);
        }
    }
    private static String singlePattern(SNode node,String role){var rules=children(node,role);if(rules.size()!=1)throw invalid("Exactly one "+role+" rule required");String pattern=get(rules.getFirst(),"pattern");if(pattern.isEmpty())throw invalid("Empty lexical pattern");Pattern.compile(pattern);return pattern;}
    private static JsonObject namedValues(SNode parent,String role){JsonObject values=new JsonObject();for(SNode child:children(parent,role)){String name=name(child);if(values.has(name))throw invalid("Duplicate "+role+" name: "+name);values.addProperty(name,get(child,"value"));}return values;}
    private static JsonArray stringValues(SNode parent,String role){JsonArray values=new JsonArray();for(SNode child:children(parent,role))values.add(get(child,"value"));return values;}
    public static List<SNode> children(SNode parent,String role){List<SNode> result=new ArrayList<>();parent.getChildren(child(parent.getConcept().getName(),role)).forEach(result::add);return result;}
    public static String get(SNode node,String property){String value=node.getProperty(property(node.getConcept().getName(),property));return value==null?"":value;}
    private static String name(SNode node){String name=get(node,"name");if(name.isEmpty())throw invalid("Missing name for "+node.getConcept().getName());return name;}
    private static boolean bool(SNode node,String property){String value=get(node,property);if(value.isEmpty())return false;if(!value.equals("true")&&!value.equals("false"))throw invalid("Expected boolean: "+property);return Boolean.parseBoolean(value);}
    private static int integer(String text,String label){try{return Integer.parseInt(text);}catch(NumberFormatException e){throw invalid("Expected integer: "+label);}}
    private static Set<String> difference(Set<String> left,Set<String> right){Set<String> result=new TreeSet<>(left);result.removeAll(right);return result;}
    private static IllegalArgumentException invalid(String message){return new IllegalArgumentException(message);}
    public static String sha256(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    public static String json(JsonObject metadata){return JSON.toJson(metadata)+"\n";}
    private static String quoted(String value){return COMPACT.toJson(value);}
    private static String constant(String name){return name.replaceAll("([a-z0-9])([A-Z])","$1_$2").toUpperCase(Locale.ROOT);}
    public static String java(JsonObject metadata){
        JsonObject syntax=metadata.getAsJsonObject("syntax");StringBuilder out=new StringBuilder("package com.cozooo.dclanguage.generated;\n\nimport java.util.List;\nimport java.util.Map;\n\npublic final class DcGeneratedSyntax {\n");
        out.append("    public static final String DEFINITION_SHA256 = ").append(quoted(metadata.get("definitionHash").getAsString())).append(";\n");
        for(var token:syntax.getAsJsonObject("tokens").entrySet())out.append("    public static final String TOKEN_").append(constant(token.getKey())).append(" = ").append(quoted(token.getValue().getAsString())).append(";\n");
        for(String key:List.of("numberPattern","bareKeyPattern"))out.append("    public static final String ").append(constant(key)).append(" = ").append(quoted(syntax.get(key).getAsString())).append(";\n");
        for(var literal:syntax.getAsJsonObject("literals").entrySet())out.append("    public static final String ").append(constant(literal.getKey())).append("_LITERAL = ").append(quoted(literal.getValue().getAsString())).append(";\n");
        appendMap(out,"TOKENS",syntax.getAsJsonObject("tokens"));appendMap(out,"ESCAPES",syntax.getAsJsonObject("escapes"));appendMap(out,"POLICIES",metadata.getAsJsonObject("policies"));
        out.append("    public record StringRule(String name, String delimiter, boolean multiline, boolean escaped) { }\n    public static final List<StringRule> STRING_RULES = List.of(\n");
        List<String> rules=new ArrayList<>();for(JsonElement rule:syntax.getAsJsonArray("stringRules")){JsonObject r=rule.getAsJsonObject();rules.add("        new StringRule("+quoted(r.get("name").getAsString())+", "+quoted(r.get("delimiter").getAsString())+", "+r.get("multiline")+", "+r.get("escaped")+")");}out.append(String.join(",\n",rules)).append("\n    );\n");
        List<String> types=new ArrayList<>();syntax.getAsJsonArray("valueTypes").forEach(t->types.add(quoted(t.getAsString())));out.append("    public static final List<String> VALUE_TYPES = List.of(").append(String.join(", ",types)).append(");\n");
        out.append("    public static boolean booleanPolicy(String name) { return Boolean.parseBoolean(requiredPolicy(name)); }\n    public static int integerPolicy(String name) { return Integer.parseInt(requiredPolicy(name)); }\n    private static String requiredPolicy(String name) { String value = POLICIES.get(name); if (value == null) throw new IllegalArgumentException(\"Unknown DC policy: \" + name); return value; }\n    private DcGeneratedSyntax() { }\n}\n");return out.toString();
    }
    private static void appendMap(StringBuilder out,String name,JsonObject values){out.append("    public static final Map<String, String> ").append(name).append(" = Map.ofEntries(\n");List<String> entries=new ArrayList<>();for(var entry:values.entrySet())entries.add("        Map.entry("+quoted(entry.getKey())+", "+quoted(entry.getValue().getAsString())+")");out.append(String.join(",\n",entries)).append("\n    );\n");}
    public static Map<String,String> files(SNode definition,String hash){JsonObject metadata=metadata(definition,hash);Map<String,String> files=new LinkedHashMap<>();files.put(JAVA_PATH,java(metadata));files.put(METADATA_PATH,json(metadata));
        files.put("java/com/cozooo/dclanguage/DcParser.java","package com.cozooo.dclanguage;\n\nfinal class DcParser extends DcParserEngine {\n    private DcParser() { }\n}\n");
        files.put("java/com/cozooo/dclanguage/DcWriter.java","package com.cozooo.dclanguage;\n\nfinal class DcWriter extends DcWriterEngine {\n    private DcWriter(DcDocument document) { super(document); }\n}\n");return files;}
    public static void emit(SNode definition,Path modelFile,Path output,boolean verify)throws IOException{
        String hash=sha256(Files.readAllBytes(modelFile));Map<String,String> files=files(definition,hash);JsonObject manifest=new JsonObject();manifest.addProperty("formatVersion",1);manifest.addProperty("source","mps/solutions/definition/models/dc.mps");manifest.addProperty("definitionHash",hash);JsonObject outputs=new JsonObject();files.forEach((path,text)->outputs.addProperty(path,sha256(text.getBytes(StandardCharsets.UTF_8))));manifest.add("outputs",outputs);files.put("generation-manifest.json",json(manifest));
        for(var file:files.entrySet()){Path path=output.resolve(file.getKey());if(verify){if(!Files.exists(path)||!Files.readString(path).equals(file.getValue()))throw new IOException("Generated DC output is stale: "+file.getKey());}else{Files.createDirectories(path.getParent());Files.writeString(path,file.getValue(),StandardCharsets.UTF_8);}}
    }
}
