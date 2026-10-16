<?xml version='1.0' encoding='UTF-8'?>
<model ref="r:5d6e019c-0f6f-4318-ac1e-e3d77a18cda3(com.cozooo.dclanguage.definitions.dc)">
  <persistence version="9" />
  <languages>
    <use id="4b259ac3-0fd5-481e-b370-5e307e623184" name="com.cozooo.dclanguage.definition" version="0" />
  </languages>
  <imports />
  <registry>
    <language id="4b259ac3-0fd5-481e-b370-5e307e623184" name="com.cozooo.dclanguage.definition">
      <concept id="2001" name="com.cozooo.dclanguage.definition.structure.Definition" flags="ng" index="C2001">
        <property id="200101" name="formatVersion" index="P200101" />
        <child id="200151" name="tokens" index="R200151" />
        <child id="200152" name="strings" index="R200152" />
        <child id="200153" name="escapes" index="R200153" />
        <child id="200154" name="valueTypes" index="R200154" />
        <child id="200155" name="policies" index="R200155" />
        <child id="200156" name="profiles" index="R200156" />
        <child id="200157" name="literals" index="R200157" />
        <child id="200158" name="numbers" index="R200158" />
        <child id="200159" name="names" index="R200159" />
      </concept>
      <concept id="2002" name="com.cozooo.dclanguage.definition.structure.SyntaxToken" flags="ng" index="C2002">
        <property id="200201" name="value" index="P200201" />
      </concept>
      <concept id="2003" name="com.cozooo.dclanguage.definition.structure.StringRule" flags="ng" index="C2003">
        <property id="200301" name="delimiter" index="P200301" />
        <property id="200302" name="multiline" index="P200302" />
        <property id="200303" name="escaped" index="P200303" />
      </concept>
      <concept id="2004" name="com.cozooo.dclanguage.definition.structure.EscapeRule" flags="ng" index="C2004">
        <property id="200401" name="value" index="P200401" />
      </concept>
      <concept id="2005" name="com.cozooo.dclanguage.definition.structure.ValueType" flags="ng" index="C2005">
        <property id="200501" name="description" index="P200501" />
      </concept>
      <concept id="2006" name="com.cozooo.dclanguage.definition.structure.Policy" flags="ng" index="C2006">
        <property id="200601" name="value" index="P200601" />
      </concept>
      <concept id="2007" name="com.cozooo.dclanguage.definition.structure.SchemaProfile" flags="ng" index="C2007">
        <property id="200701" name="description" index="P200701" />
        <child id="200751" name="filePatterns" index="R200751" />
        <child id="200752" name="categories" index="R200752" />
      </concept>
      <concept id="2008" name="com.cozooo.dclanguage.definition.structure.Category" flags="ng" index="C2008">
        <property id="200801" name="required" index="P200801" />
        <property id="200802" name="description" index="P200802" />
        <child id="200851" name="fields" index="R200851" />
        <child id="200852" name="categories" index="R200852" />
      </concept>
      <concept id="2009" name="com.cozooo.dclanguage.definition.structure.Field" flags="ng" index="C2009">
        <property id="200901" name="type" index="P200901" />
        <property id="200902" name="required" index="P200902" />
        <property id="200903" name="description" index="P200903" />
        <property id="200904" name="defaultValue" index="P200904" />
        <child id="200951" name="enumValues" index="R200951" />
        <child id="200952" name="constraints" index="R200952" />
      </concept>
      <concept id="2010" name="com.cozooo.dclanguage.definition.structure.Constraint" flags="ng" index="C2010">
        <property id="201001" name="value" index="P201001" />
      </concept>
      <concept id="2011" name="com.cozooo.dclanguage.definition.structure.StringValue" flags="ng" index="C2011">
        <property id="201101" name="value" index="P201101" />
      </concept>
      <concept id="2012" name="com.cozooo.dclanguage.definition.structure.LiteralRule" flags="ng" index="C2012">
        <property id="201201" name="value" index="P201201" />
        <property id="201202" name="type" index="P201202" />
      </concept>
      <concept id="2013" name="com.cozooo.dclanguage.definition.structure.NumberRule" flags="ng" index="C2013">
        <property id="201301" name="pattern" index="P201301" />
      </concept>
      <concept id="2014" name="com.cozooo.dclanguage.definition.structure.NameRule" flags="ng" index="C2014">
        <property id="201401" name="pattern" index="P201401" />
      </concept>
    </language>
    <language id="ceab5195-25ea-4f22-9b92-103b95ca8c0c" name="jetbrains.mps.lang.core">
      <concept id="1169194658468" name="jetbrains.mps.lang.core.structure.INamedConcept" flags="ngI" index="INamed">
        <property id="1169194664001" name="name" index="Name" />
      </concept>
    </language>
  </registry>
  <node concept="C2001" id="cdh">
    <property role="Name" value="dc" />
    <property role="P200101" value="1" />
    <node concept="C2002" id="cdi" role="R200151">
      <property role="Name" value="assignment" />
      <property role="P200201" value="=" />
    </node>
    <node concept="C2002" id="cdj" role="R200151">
      <property role="Name" value="categoryOpen" />
      <property role="P200201" value="[" />
    </node>
    <node concept="C2002" id="cdk" role="R200151">
      <property role="Name" value="categoryClose" />
      <property role="P200201" value="]" />
    </node>
    <node concept="C2002" id="cdl" role="R200151">
      <property role="Name" value="categorySeparator" />
      <property role="P200201" value="." />
    </node>
    <node concept="C2002" id="cdm" role="R200151">
      <property role="Name" value="block" />
      <property role="P200201" value=":" />
    </node>
    <node concept="C2002" id="cdn" role="R200151">
      <property role="Name" value="arrayOpen" />
      <property role="P200201" value="[" />
    </node>
    <node concept="C2002" id="cdo" role="R200151">
      <property role="Name" value="arrayClose" />
      <property role="P200201" value="]" />
    </node>
    <node concept="C2002" id="cdp" role="R200151">
      <property role="Name" value="objectOpen" />
      <property role="P200201" value="{" />
    </node>
    <node concept="C2002" id="cdq" role="R200151">
      <property role="Name" value="objectClose" />
      <property role="P200201" value="}" />
    </node>
    <node concept="C2002" id="cdr" role="R200151">
      <property role="Name" value="itemSeparator" />
      <property role="P200201" value="," />
    </node>
    <node concept="C2002" id="cds" role="R200151">
      <property role="Name" value="objectSeparator" />
      <property role="P200201" value=":" />
    </node>
    <node concept="C2002" id="cdt" role="R200151">
      <property role="Name" value="escape" />
      <property role="P200201" value="\" />
    </node>
    <node concept="C2002" id="cdu" role="R200151">
      <property role="Name" value="comment" />
      <property role="P200201" value="#" />
    </node>
    <node concept="C2002" id="cdv" role="R200151">
      <property role="Name" value="lineComment" />
      <property role="P200201" value="//" />
    </node>
    <node concept="C2002" id="cdw" role="R200151">
      <property role="Name" value="unicodeEscape" />
      <property role="P200201" value="u" />
    </node>
    <node concept="C2003" id="cdx" role="R200152">
      <property role="Name" value="multilineBasic" />
      <property role="P200301" value="&quot;&quot;&quot;" />
      <property role="P200302" value="true" />
      <property role="P200303" value="false" />
    </node>
    <node concept="C2003" id="cdy" role="R200152">
      <property role="Name" value="multilineLiteral" />
      <property role="P200301" value="'''" />
      <property role="P200302" value="true" />
      <property role="P200303" value="false" />
    </node>
    <node concept="C2003" id="cdz" role="R200152">
      <property role="Name" value="basic" />
      <property role="P200301" value="&quot;" />
      <property role="P200302" value="false" />
      <property role="P200303" value="true" />
    </node>
    <node concept="C2003" id="cd$" role="R200152">
      <property role="Name" value="literal" />
      <property role="P200301" value="'" />
      <property role="P200302" value="false" />
      <property role="P200303" value="false" />
    </node>
    <node concept="C2004" id="cd_" role="R200153">
      <property role="Name" value="n" />
      <property role="P200401" value="U+000A" />
    </node>
    <node concept="C2004" id="cdA" role="R200153">
      <property role="Name" value="r" />
      <property role="P200401" value="U+000D" />
    </node>
    <node concept="C2004" id="cdB" role="R200153">
      <property role="Name" value="t" />
      <property role="P200401" value="U+0009" />
    </node>
    <node concept="C2004" id="cdC" role="R200153">
      <property role="Name" value="b" />
      <property role="P200401" value="U+0008" />
    </node>
    <node concept="C2004" id="cdD" role="R200153">
      <property role="Name" value="f" />
      <property role="P200401" value="U+000C" />
    </node>
    <node concept="C2004" id="cdE" role="R200153">
      <property role="Name" value="&quot;" />
      <property role="P200401" value="U+0022" />
    </node>
    <node concept="C2004" id="cdF" role="R200153">
      <property role="Name" value="\" />
      <property role="P200401" value="U+005C" />
    </node>
    <node concept="C2004" id="cdG" role="R200153">
      <property role="Name" value="/" />
      <property role="P200401" value="U+002F" />
    </node>
    <node concept="C2005" id="cdH" role="R200154">
      <property role="Name" value="string" />
      <property role="P200501" value="string value" />
    </node>
    <node concept="C2005" id="cdI" role="R200154">
      <property role="Name" value="boolean" />
      <property role="P200501" value="boolean value" />
    </node>
    <node concept="C2005" id="cdJ" role="R200154">
      <property role="Name" value="number" />
      <property role="P200501" value="number value" />
    </node>
    <node concept="C2005" id="cdK" role="R200154">
      <property role="Name" value="integer" />
      <property role="P200501" value="integer value" />
    </node>
    <node concept="C2005" id="cdL" role="R200154">
      <property role="Name" value="null" />
      <property role="P200501" value="null value" />
    </node>
    <node concept="C2005" id="cdM" role="R200154">
      <property role="Name" value="object" />
      <property role="P200501" value="object value" />
    </node>
    <node concept="C2005" id="cdN" role="R200154">
      <property role="Name" value="array" />
      <property role="P200501" value="array value" />
    </node>
    <node concept="C2005" id="cdO" role="R200154">
      <property role="Name" value="any" />
      <property role="P200501" value="any value" />
    </node>
    <node concept="C2006" id="cdP" role="R200155">
      <property role="Name" value="allowBareValues" />
      <property role="P200601" value="true" />
    </node>
    <node concept="C2006" id="cdQ" role="R200155">
      <property role="Name" value="allowEmptyAssignment" />
      <property role="P200601" value="true" />
    </node>
    <node concept="C2006" id="cdR" role="R200155">
      <property role="Name" value="allowTrailingComma" />
      <property role="P200601" value="true" />
    </node>
    <node concept="C2006" id="cdS" role="R200155">
      <property role="Name" value="allowNewlineSeparator" />
      <property role="P200601" value="true" />
    </node>
    <node concept="C2006" id="cdT" role="R200155">
      <property role="Name" value="caseInsensitiveBooleans" />
      <property role="P200601" value="true" />
    </node>
    <node concept="C2006" id="cdU" role="R200155">
      <property role="Name" value="allowDottedCategories" />
      <property role="P200601" value="true" />
    </node>
    <node concept="C2006" id="cdV" role="R200155">
      <property role="Name" value="allowColonCategories" />
      <property role="P200601" value="true" />
    </node>
    <node concept="C2006" id="cdW" role="R200155">
      <property role="Name" value="allowBareEntries" />
      <property role="P200601" value="true" />
    </node>
    <node concept="C2006" id="cdX" role="R200155">
      <property role="Name" value="maxDepth" />
      <property role="P200601" value="256" />
    </node>
    <node concept="C2006" id="cdY" role="R200155">
      <property role="Name" value="preserveUnknownEscapes" />
      <property role="P200601" value="true" />
    </node>
    <node concept="C2006" id="cdZ" role="R200155">
      <property role="Name" value="allowBom" />
      <property role="P200601" value="true" />
    </node>
    <node concept="C2006" id="ce0" role="R200155">
      <property role="Name" value="indentWidth" />
      <property role="P200601" value="2" />
    </node>
    <node concept="C2012" id="ce1" role="R200157">
      <property role="Name" value="true" />
      <property role="P201201" value="true" />
      <property role="P201202" value="boolean" />
    </node>
    <node concept="C2012" id="ce2" role="R200157">
      <property role="Name" value="false" />
      <property role="P201201" value="false" />
      <property role="P201202" value="boolean" />
    </node>
    <node concept="C2012" id="ce3" role="R200157">
      <property role="Name" value="null" />
      <property role="P201201" value="null" />
      <property role="P201202" value="null" />
    </node>
    <node concept="C2013" id="ce4" role="R200158">
      <property role="Name" value="decimal" />
      <property role="P201301" value="[+-]?(?:[0-9]+(?:\.[0-9]+)?|\.[0-9]+)(?:[eE][+-]?[0-9]+)?" />
    </node>
    <node concept="C2014" id="ce5" role="R200159">
      <property role="Name" value="bareKey" />
      <property role="P201401" value="[A-Za-z_][A-Za-z0-9_-]*" />
    </node>
  </node>
</model>