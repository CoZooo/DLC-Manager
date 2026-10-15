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
    <node concept="C2007" id="ce6" role="R200156">
      <property role="Name" value="info" />
      <property role="P200701" value="DLC file information&#10;Stored in &lt;DLC&gt;/info" />
      <node concept="C2011" id="ce7" role="R200751">
        <property role="P201101" value="**/info/**/*.dc" />
      </node>
      <node concept="C2008" id="ce8" role="R200752">
        <property role="Name" value="basic" />
        <property role="P200801" value="false" />
        <property role="P200802" value="" />
        <node concept="C2009" id="ce9" role="R200851">
          <property role="Name" value="identifier" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Logical identifier used for dependency, conflict, and preset matching." />
        </node>
        <node concept="C2009" id="cea" role="R200851">
          <property role="Name" value="mode" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Match by SHA-256 content (hash, default) or filename (name)." />
          <property role="P200904" value="&quot;hash&quot;" />
          <node concept="C2011" id="ceb" role="R200951">
            <property role="P201101" value="hash" />
          </node>
          <node concept="C2011" id="cec" role="R200951">
            <property role="P201101" value="name" />
          </node>
        </node>
        <node concept="C2009" id="ced" role="R200851">
          <property role="Name" value="file_name" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="DLC file name (including extension)." />
        </node>
        <node concept="C2009" id="cee" role="R200851">
          <property role="Name" value="fileNames" />
          <property role="P200901" value="array" />
          <property role="P200902" value="false" />
          <property role="P200903" value="" />
        </node>
        <node concept="C2009" id="cef" role="R200851">
          <property role="Name" value="type" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="DLC type. Supported values: mod, resourcepack, datapack, gunpack, config, others." />
          <node concept="C2011" id="ceg" role="R200951">
            <property role="P201101" value="mod" />
          </node>
          <node concept="C2011" id="ceh" role="R200951">
            <property role="P201101" value="resourcepack" />
          </node>
          <node concept="C2011" id="cei" role="R200951">
            <property role="P201101" value="datapack" />
          </node>
          <node concept="C2011" id="cej" role="R200951">
            <property role="P201101" value="gunpack" />
          </node>
          <node concept="C2011" id="cek" role="R200951">
            <property role="P201101" value="config" />
          </node>
          <node concept="C2011" id="cel" role="R200951">
            <property role="P201101" value="others" />
          </node>
        </node>
        <node concept="C2009" id="cem" role="R200851">
          <property role="Name" value="appliedTarget" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Applied target path, enter a relative path." />
        </node>
        <node concept="C2009" id="cen" role="R200851">
          <property role="Name" value="RedirectUrl" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Link opened when the DLC name is double-clicked." />
        </node>
        <node concept="C2009" id="ceo" role="R200851">
          <property role="Name" value="isHidden" />
          <property role="P200901" value="boolean" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Whether this entry is hidden, useful for libraries or internal prerequisites." />
        </node>
        <node concept="C2009" id="cep" role="R200851">
          <property role="Name" value="AllowEnableByAll" />
          <property role="P200901" value="boolean" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Whether this entry follows Enable All." />
        </node>
        <node concept="C2009" id="ceq" role="R200851">
          <property role="Name" value="alarm" />
          <property role="P200901" value="boolean" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Whether to show the alarm confirmation page before enabling." />
        </node>
      </node>
      <node concept="C2008" id="cer" role="R200752">
        <property role="Name" value="dependencies" />
        <property role="P200801" value="false" />
        <property role="P200802" value="" />
        <node concept="C2009" id="ces" role="R200851">
          <property role="Name" value="*" />
          <property role="P200901" value="object|string|array" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Dependency identifier or relative path list." />
        </node>
      </node>
      <node concept="C2008" id="cet" role="R200752">
        <property role="Name" value="conflicts" />
        <property role="P200801" value="false" />
        <property role="P200802" value="" />
        <node concept="C2009" id="ceu" role="R200851">
          <property role="Name" value="*" />
          <property role="P200901" value="object|string|array" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Conflict identifier or relative path list." />
        </node>
      </node>
      <node concept="C2008" id="cev" role="R200752">
        <property role="Name" value="DisplayName" />
        <property role="P200801" value="false" />
        <property role="P200802" value="" />
        <node concept="C2009" id="cew" role="R200851">
          <property role="Name" value="*" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Localized value keyed by language code." />
        </node>
      </node>
      <node concept="C2008" id="cex" role="R200752">
        <property role="Name" value="tip" />
        <property role="P200801" value="false" />
        <property role="P200802" value="" />
        <node concept="C2009" id="cey" role="R200851">
          <property role="Name" value="*" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Localized value keyed by language code." />
        </node>
      </node>
    </node>
    <node concept="C2007" id="cez" role="R200156">
      <property role="Name" value="remote" />
      <property role="P200701" value="Downloadable DLC&#10;Stored in &lt;DLC&gt;/remote" />
      <node concept="C2011" id="ce$" role="R200751">
        <property role="P201101" value="**/remote/**/*.dc" />
      </node>
      <node concept="C2008" id="ce_" role="R200752">
        <property role="Name" value="download" />
        <property role="P200801" value="false" />
        <property role="P200802" value="" />
        <node concept="C2009" id="ceA" role="R200851">
          <property role="Name" value="modrinth_projectid" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Modrinth project ID or slug." />
        </node>
        <node concept="C2009" id="ceB" role="R200851">
          <property role="Name" value="modrinth_versionid" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Modrinth version ID or version string." />
        </node>
        <node concept="C2009" id="ceC" role="R200851">
          <property role="Name" value="curseforge_projectid" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="CurseForge project ID or slug." />
        </node>
        <node concept="C2009" id="ceD" role="R200851">
          <property role="Name" value="curseforge_fileid" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="CurseForge file ID, version string, or file name." />
        </node>
        <node concept="C2009" id="ceE" role="R200851">
          <property role="Name" value="directUrl" />
          <property role="P200901" value="string|array" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Direct links. One URL per line." />
        </node>
        <node concept="C2009" id="ceF" role="R200851">
          <property role="Name" value="priority" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Adjust download source priority order." />
          <node concept="C2011" id="ceG" role="R200951">
            <property role="P201101" value="modrinth" />
          </node>
          <node concept="C2011" id="ceH" role="R200951">
            <property role="P201101" value="curseforge" />
          </node>
          <node concept="C2011" id="ceI" role="R200951">
            <property role="P201101" value="directUrl" />
          </node>
        </node>
      </node>
      <node concept="C2008" id="ceJ" role="R200752">
        <property role="Name" value="basic" />
        <property role="P200801" value="false" />
        <property role="P200802" value="" />
        <node concept="C2009" id="ceK" role="R200851">
          <property role="Name" value="identifier" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Logical identifier used for dependency, conflict, and preset matching." />
        </node>
        <node concept="C2009" id="ceL" role="R200851">
          <property role="Name" value="mode" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Match by SHA-256 content (hash, default) or filename (name)." />
          <property role="P200904" value="&quot;hash&quot;" />
          <node concept="C2011" id="ceM" role="R200951">
            <property role="P201101" value="hash" />
          </node>
          <node concept="C2011" id="ceN" role="R200951">
            <property role="P201101" value="name" />
          </node>
        </node>
        <node concept="C2009" id="ceO" role="R200851">
          <property role="Name" value="file_name" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="DLC file name (including extension)." />
        </node>
        <node concept="C2009" id="ceP" role="R200851">
          <property role="Name" value="fileNames" />
          <property role="P200901" value="array" />
          <property role="P200902" value="false" />
          <property role="P200903" value="" />
        </node>
        <node concept="C2009" id="ceQ" role="R200851">
          <property role="Name" value="type" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="DLC type. Supported values: mod, resourcepack, datapack, gunpack, config, others." />
          <node concept="C2011" id="ceR" role="R200951">
            <property role="P201101" value="mod" />
          </node>
          <node concept="C2011" id="ceS" role="R200951">
            <property role="P201101" value="resourcepack" />
          </node>
          <node concept="C2011" id="ceT" role="R200951">
            <property role="P201101" value="datapack" />
          </node>
          <node concept="C2011" id="ceU" role="R200951">
            <property role="P201101" value="gunpack" />
          </node>
          <node concept="C2011" id="ceV" role="R200951">
            <property role="P201101" value="config" />
          </node>
          <node concept="C2011" id="ceW" role="R200951">
            <property role="P201101" value="others" />
          </node>
          <node concept="C2011" id="ceX" role="R200951">
            <property role="P201101" value="dlcpack" />
          </node>
        </node>
        <node concept="C2009" id="ceY" role="R200851">
          <property role="Name" value="appliedTarget" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Applied target path, enter a relative path." />
        </node>
        <node concept="C2009" id="ceZ" role="R200851">
          <property role="Name" value="RedirectUrl" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Link opened when the DLC name is double-clicked." />
        </node>
        <node concept="C2009" id="cf0" role="R200851">
          <property role="Name" value="isHidden" />
          <property role="P200901" value="boolean" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Whether this entry is hidden, useful for libraries or internal prerequisites." />
        </node>
        <node concept="C2009" id="cf1" role="R200851">
          <property role="Name" value="AllowEnableByAll" />
          <property role="P200901" value="boolean" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Whether this entry follows Enable All." />
        </node>
        <node concept="C2009" id="cf2" role="R200851">
          <property role="Name" value="alarm" />
          <property role="P200901" value="boolean" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Whether to show the alarm confirmation page before enabling." />
        </node>
      </node>
      <node concept="C2008" id="cf3" role="R200752">
        <property role="Name" value="dependencies" />
        <property role="P200801" value="false" />
        <property role="P200802" value="" />
        <node concept="C2009" id="cf4" role="R200851">
          <property role="Name" value="*" />
          <property role="P200901" value="object|string|array" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Dependency identifier or relative path list." />
        </node>
      </node>
      <node concept="C2008" id="cf5" role="R200752">
        <property role="Name" value="conflicts" />
        <property role="P200801" value="false" />
        <property role="P200802" value="" />
        <node concept="C2009" id="cf6" role="R200851">
          <property role="Name" value="*" />
          <property role="P200901" value="object|string|array" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Conflict identifier or relative path list." />
        </node>
      </node>
      <node concept="C2008" id="cf7" role="R200752">
        <property role="Name" value="DisplayName" />
        <property role="P200801" value="false" />
        <property role="P200802" value="" />
        <node concept="C2009" id="cf8" role="R200851">
          <property role="Name" value="*" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Localized value keyed by language code." />
        </node>
      </node>
      <node concept="C2008" id="cf9" role="R200752">
        <property role="Name" value="tip" />
        <property role="P200801" value="false" />
        <property role="P200802" value="" />
        <node concept="C2009" id="cfa" role="R200851">
          <property role="Name" value="*" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Localized value keyed by language code." />
        </node>
      </node>
    </node>
    <node concept="C2007" id="cfb" role="R200156">
      <property role="Name" value="required" />
      <property role="P200701" value="Required resource config&#10;Stored in &lt;DLC&gt;/required" />
      <node concept="C2011" id="cfc" role="R200751">
        <property role="P201101" value="**/required/**/*.dc" />
      </node>
      <node concept="C2008" id="cfd" role="R200752">
        <property role="Name" value="download" />
        <property role="P200801" value="false" />
        <property role="P200802" value="" />
        <node concept="C2009" id="cfe" role="R200851">
          <property role="Name" value="modrinth_projectid" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Modrinth project ID or slug." />
        </node>
        <node concept="C2009" id="cff" role="R200851">
          <property role="Name" value="modrinth_versionid" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Modrinth version ID or version string." />
        </node>
        <node concept="C2009" id="cfg" role="R200851">
          <property role="Name" value="curseforge_projectid" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="CurseForge project ID or slug." />
        </node>
        <node concept="C2009" id="cfh" role="R200851">
          <property role="Name" value="curseforge_fileid" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="CurseForge file ID, version string, or file name." />
        </node>
        <node concept="C2009" id="cfi" role="R200851">
          <property role="Name" value="directUrl" />
          <property role="P200901" value="string|array" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Direct links. One URL per line." />
        </node>
        <node concept="C2009" id="cfj" role="R200851">
          <property role="Name" value="priority" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Adjust download source priority order." />
          <node concept="C2011" id="cfk" role="R200951">
            <property role="P201101" value="modrinth" />
          </node>
          <node concept="C2011" id="cfl" role="R200951">
            <property role="P201101" value="curseforge" />
          </node>
          <node concept="C2011" id="cfm" role="R200951">
            <property role="P201101" value="directUrl" />
          </node>
        </node>
      </node>
      <node concept="C2008" id="cfn" role="R200752">
        <property role="Name" value="basic" />
        <property role="P200801" value="false" />
        <property role="P200802" value="" />
        <node concept="C2009" id="cfo" role="R200851">
          <property role="Name" value="identifier" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Logical identifier used for dependency matching." />
        </node>
        <node concept="C2009" id="cfp" role="R200851">
          <property role="Name" value="mode" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Match by SHA-256 content (hash, default) or filename (name)." />
          <property role="P200904" value="&quot;hash&quot;" />
          <node concept="C2011" id="cfq" role="R200951">
            <property role="P201101" value="hash" />
          </node>
          <node concept="C2011" id="cfr" role="R200951">
            <property role="P201101" value="name" />
          </node>
        </node>
        <node concept="C2009" id="cfs" role="R200851">
          <property role="Name" value="file_name" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="DLC file name (including extension), used as the final downloaded file name." />
        </node>
        <node concept="C2009" id="cft" role="R200851">
          <property role="Name" value="appliedTarget" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Applied target path, enter a relative path." />
        </node>
      </node>
      <node concept="C2008" id="cfu" role="R200752">
        <property role="Name" value="dependencies" />
        <property role="P200801" value="false" />
        <property role="P200802" value="" />
        <node concept="C2009" id="cfv" role="R200851">
          <property role="Name" value="*" />
          <property role="P200901" value="object|string|array" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Dependency identifier or relative path list." />
        </node>
      </node>
    </node>
    <node concept="C2007" id="cfw" role="R200156">
      <property role="Name" value="preset" />
      <property role="P200701" value="Preset config&#10;Stored in &lt;DLC&gt;/preset" />
      <node concept="C2011" id="cfx" role="R200751">
        <property role="P201101" value="**/preset/**/*.dc" />
      </node>
      <node concept="C2011" id="cfy" role="R200751">
        <property role="P201101" value="**/preset.dc" />
      </node>
      <node concept="C2008" id="cfz" role="R200752">
        <property role="Name" value="basic" />
        <property role="P200801" value="false" />
        <property role="P200802" value="" />
        <node concept="C2009" id="cf$" role="R200851">
          <property role="Name" value="identifier" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Preset identifier." />
        </node>
        <node concept="C2009" id="cf_" role="R200851">
          <property role="Name" value="alarm" />
          <property role="P200901" value="boolean" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Whether to show an alarm confirmation before enabling the preset." />
        </node>
      </node>
      <node concept="C2008" id="cfA" role="R200752">
        <property role="Name" value="dependencies" />
        <property role="P200801" value="false" />
        <property role="P200802" value="" />
        <node concept="C2009" id="cfB" role="R200851">
          <property role="Name" value="*" />
          <property role="P200901" value="object|string|array" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Dependency identifier or relative path list." />
        </node>
      </node>
      <node concept="C2008" id="cfC" role="R200752">
        <property role="Name" value="conflicts" />
        <property role="P200801" value="false" />
        <property role="P200802" value="" />
        <node concept="C2009" id="cfD" role="R200851">
          <property role="Name" value="*" />
          <property role="P200901" value="object|string|array" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Conflict identifier or relative path list." />
        </node>
      </node>
      <node concept="C2008" id="cfE" role="R200752">
        <property role="Name" value="DisplayName" />
        <property role="P200801" value="false" />
        <property role="P200802" value="" />
        <node concept="C2009" id="cfF" role="R200851">
          <property role="Name" value="*" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Localized value keyed by language code." />
        </node>
      </node>
      <node concept="C2008" id="cfG" role="R200752">
        <property role="Name" value="tip" />
        <property role="P200801" value="false" />
        <property role="P200802" value="" />
        <node concept="C2009" id="cfH" role="R200851">
          <property role="Name" value="*" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Localized value keyed by language code." />
        </node>
      </node>
    </node>
    <node concept="C2007" id="cfI" role="R200156">
      <property role="Name" value="notice" />
      <property role="P200701" value="Information config&#10;Usually stored as &lt;DLC&gt;/notice.dc" />
      <node concept="C2011" id="cfJ" role="R200751">
        <property role="P201101" value="**/notice.dc" />
      </node>
      <node concept="C2008" id="cfK" role="R200752">
        <property role="Name" value="@root" />
        <property role="P200801" value="false" />
        <property role="P200802" value="" />
        <node concept="C2009" id="cfL" role="R200851">
          <property role="Name" value="isNoticeEnabled" />
          <property role="P200901" value="boolean" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Whether the information feature is enabled." />
        </node>
        <node concept="C2009" id="cfM" role="R200851">
          <property role="Name" value="ShowFrequency" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Information display frequency.&#10;perlaunchmanager: show each time DLC Manager opens.&#10;pergamesession: show once per game process.&#10;perlaunchgame: show once each game launch.&#10;once: show only once." />
          <node concept="C2011" id="cfN" role="R200951">
            <property role="P201101" value="perlaunchmanager" />
          </node>
          <node concept="C2011" id="cfO" role="R200951">
            <property role="P201101" value="pergamesession" />
          </node>
          <node concept="C2011" id="cfP" role="R200951">
            <property role="P201101" value="perlaunchgame" />
          </node>
          <node concept="C2011" id="cfQ" role="R200951">
            <property role="P201101" value="once" />
          </node>
        </node>
      </node>
      <node concept="C2008" id="cfR" role="R200752">
        <property role="Name" value="Title" />
        <property role="P200801" value="false" />
        <property role="P200802" value="" />
        <node concept="C2009" id="cfS" role="R200851">
          <property role="Name" value="*" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Localized value keyed by language code." />
        </node>
      </node>
      <node concept="C2008" id="cfT" role="R200752">
        <property role="Name" value="Subhead" />
        <property role="P200801" value="false" />
        <property role="P200802" value="" />
        <node concept="C2009" id="cfU" role="R200851">
          <property role="Name" value="enabled" />
          <property role="P200901" value="boolean" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Whether the subhead is enabled." />
        </node>
        <node concept="C2009" id="cfV" role="R200851">
          <property role="Name" value="*" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Localized value keyed by language code." />
        </node>
      </node>
      <node concept="C2008" id="cfW" role="R200752">
        <property role="Name" value="ContentUrl" />
        <property role="P200801" value="false" />
        <property role="P200802" value="" />
        <node concept="C2009" id="cfX" role="R200851">
          <property role="Name" value="*" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Localized value keyed by language code." />
        </node>
      </node>
      <node concept="C2008" id="cfY" role="R200752">
        <property role="Name" value="Contents" />
        <property role="P200801" value="false" />
        <property role="P200802" value="" />
        <node concept="C2009" id="cfZ" role="R200851">
          <property role="Name" value="*" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Localized value keyed by language code." />
        </node>
      </node>
    </node>
    <node concept="C2007" id="cg0" role="R200156">
      <property role="Name" value="redirect" />
      <property role="P200701" value="Redirect config&#10;Stored in &lt;DLC&gt;/remote/redirect" />
      <node concept="C2011" id="cg1" role="R200751">
        <property role="P201101" value="**/redirect.dc" />
      </node>
      <node concept="C2008" id="cg2" role="R200752">
        <property role="Name" value="@root" />
        <property role="P200801" value="false" />
        <property role="P200802" value="" />
        <node concept="C2009" id="cg3" role="R200851">
          <property role="Name" value="RedirectUrl" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Target redirect URL." />
        </node>
        <node concept="C2009" id="cg4" role="R200851">
          <property role="Name" value="available" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Visibility control. Empty means visible for all; hidden hides it for all languages." />
        </node>
      </node>
      <node concept="C2008" id="cg5" role="R200752">
        <property role="Name" value="DisplayName" />
        <property role="P200801" value="false" />
        <property role="P200802" value="" />
        <node concept="C2009" id="cg6" role="R200851">
          <property role="Name" value="*" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Localized value keyed by language code." />
        </node>
      </node>
      <node concept="C2008" id="cg7" role="R200752">
        <property role="Name" value="notice" />
        <property role="P200801" value="false" />
        <property role="P200802" value="" />
        <node concept="C2009" id="cg8" role="R200851">
          <property role="Name" value="*" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Localized value keyed by language code." />
        </node>
      </node>
      <node concept="C2008" id="cg9" role="R200752">
        <property role="Name" value="tip" />
        <property role="P200801" value="false" />
        <property role="P200802" value="" />
        <node concept="C2009" id="cga" role="R200851">
          <property role="Name" value="*" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Localized value keyed by language code." />
        </node>
      </node>
    </node>
    <node concept="C2007" id="cgb" role="R200156">
      <property role="Name" value="update" />
      <property role="P200701" value="Hot-update package config" />
      <node concept="C2011" id="cgc" role="R200751">
        <property role="P201101" value="**/update.dc" />
      </node>
      <node concept="C2008" id="cgd" role="R200752">
        <property role="Name" value="@root" />
        <property role="P200801" value="false" />
        <property role="P200802" value="" />
        <node concept="C2009" id="cge" role="R200851">
          <property role="Name" value="isHidden" />
          <property role="P200901" value="boolean" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Whether to hide this update package in the update list." />
        </node>
        <node concept="C2009" id="cgf" role="R200851">
          <property role="Name" value="UpdateDLCM" />
          <property role="P200901" value="string|boolean" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Update DLC Manager." />
        </node>
      </node>
      <node concept="C2008" id="cgg" role="R200752">
        <property role="Name" value="version" />
        <property role="P200801" value="false" />
        <property role="P200802" value="" />
        <node concept="C2009" id="pU1" role="R200851">
          <property role="Name" value="dlc_old" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Legacy alias for dlc." />
        </node>
        <node concept="C2009" id="cgh" role="R200851">
          <property role="Name" value="platform" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Target platform for this update package. Supported values: universal, client, server." />
          <node concept="C2011" id="cgi" role="R200951">
            <property role="P201101" value="universal" />
          </node>
          <node concept="C2011" id="cgj" role="R200951">
            <property role="P201101" value="client" />
          </node>
          <node concept="C2011" id="cgk" role="R200951">
            <property role="P201101" value="server" />
          </node>
        </node>
        <node concept="C2009" id="cgl" role="R200851">
          <property role="Name" value="base" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Required base version before applying this update." />
        </node>
        <node concept="C2009" id="cgm" role="R200851">
          <property role="Name" value="base_new" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="New base version after applying the update, leave empty if unchanged." />
        </node>
        <node concept="C2009" id="cgn" role="R200851">
          <property role="Name" value="dlc" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Required DLC version before applying this update." />
        </node>
        <node concept="C2009" id="cgo" role="R200851">
          <property role="Name" value="dlc_new" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="New DLC version after applying the update, leave empty if unchanged." />
        </node>
      </node>
      <node concept="C2008" id="cgp" role="R200752">
        <property role="Name" value="changelog" />
        <property role="P200801" value="false" />
        <property role="P200802" value="" />
        <node concept="C2009" id="cgq" role="R200851">
          <property role="Name" value="*" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Localized value keyed by language code." />
        </node>
      </node>
      <node concept="C2008" id="cgr" role="R200752">
        <property role="Name" value="file" />
        <property role="P200801" value="false" />
        <property role="P200802" value="" />
        <node concept="C2009" id="pU24" role="R200851">
          <property role="Name" value="changelog" />
          <property role="P200901" value="any" />
          <property role="P200902" value="false" />
          <property role="P200903" value="" />
        </node>
        <node concept="C2008" id="cgs" role="R200852">
          <property role="Name" value="del" />
          <property role="P200801" value="false" />
          <property role="P200802" value="" />
          <node concept="C2009" id="cgt" role="R200851">
            <property role="Name" value="entries" />
            <property role="P200901" value="string" />
            <property role="P200902" value="false" />
            <property role="P200903" value="Relative path of the file to delete. Supports single-segment * wildcards. A mods/ entry may contain an optional SHA-256 hash used only when its file name does not match." />
          </node>
          <node concept="C2008" id="hD1" role="R200852">
            <property role="Name" value="*" />
            <property role="P200801" value="false" />
            <property role="P200802" value="Delete path with optional hash fallback for files under mods/. File names are matched first." />
            <node concept="C2009" id="hD2" role="R200851">
              <property role="Name" value="hash" />
              <property role="P200901" value="string" />
              <property role="P200902" value="false" />
              <property role="P200903" value="SHA-256 fallback when the mods/ file name is not found. The parent directory still matches by path." />
            </node>
          </node>
        </node>
        <node concept="C2008" id="cgv" role="R200852">
          <property role="Name" value="add" />
          <property role="P200801" value="false" />
          <property role="P200802" value="" />
          <node concept="C2008" id="pU4" role="R200852">
            <property role="Name" value="*" />
            <property role="P200801" value="false" />
            <property role="P200802" value="" />
            <node concept="C2009" id="pU2" role="R200851">
              <property role="Name" value="appliedTarget" />
              <property role="P200901" value="string" />
              <property role="P200902" value="false" />
              <property role="P200903" value="Destination path." />
            </node>
            <node concept="C2009" id="pU3" role="R200851">
              <property role="Name" value="override" />
              <property role="P200901" value="string|boolean" />
              <property role="P200902" value="false" />
              <property role="P200903" value="Overwrite mode: true, false, all, none." />
            </node>
          </node>
          <node concept="C2009" id="cgw" role="R200851">
            <property role="Name" value="entries" />
            <property role="P200901" value="object" />
            <property role="P200902" value="false" />
            <property role="P200903" value="Add-file rule. Override mode:&#10;Checked: overwrite (true), replace every file inside the folder.&#10;Unchecked: do not overwrite (false), skip files with the same name inside the folder.&#10;all: replace the entire folder, adjust manually in raw mode.&#10;none: skip when a folder with the same name already exists, adjust manually in raw mode." />
          </node>
        </node>
        <node concept="C2008" id="cgy" role="R200852">
          <property role="Name" value="move" />
          <property role="P200801" value="false" />
          <property role="P200802" value="" />
          <node concept="C2009" id="pU5" role="R200851">
            <property role="Name" value="*" />
            <property role="P200901" value="string" />
            <property role="P200902" value="false" />
            <property role="P200903" value="Destination path." />
          </node>
          <node concept="C2009" id="cgz" role="R200851">
            <property role="Name" value="entries" />
            <property role="P200901" value="object" />
            <property role="P200902" value="false" />
            <property role="P200903" value="Move rule: from path =&gt; to path." />
          </node>
        </node>
        <node concept="C2008" id="cg_" role="R200852">
          <property role="Name" value="modify" />
          <property role="P200801" value="false" />
          <property role="P200802" value="Edit [file].modify in raw mode." />
          <node concept="C2008" id="pU10" role="R200852">
            <property role="Name" value="*" />
            <property role="P200801" value="false" />
            <property role="P200802" value="" />
            <node concept="C2009" id="pU6" role="R200851">
              <property role="Name" value="path" />
              <property role="P200901" value="string" />
              <property role="P200902" value="false" />
              <property role="P200903" value="" />
            </node>
            <node concept="C2009" id="pU7" role="R200851">
              <property role="Name" value="find" />
              <property role="P200901" value="string" />
              <property role="P200902" value="false" />
              <property role="P200903" value="" />
            </node>
            <node concept="C2009" id="pU8" role="R200851">
              <property role="Name" value="replace" />
              <property role="P200901" value="string" />
              <property role="P200902" value="false" />
              <property role="P200903" value="" />
            </node>
            <node concept="C2009" id="pU9" role="R200851">
              <property role="Name" value="which" />
              <property role="P200901" value="string|number" />
              <property role="P200902" value="false" />
              <property role="P200903" value="" />
            </node>
          </node>
        </node>
      </node>
      <node concept="C2008" id="pU23" role="R200752">
        <property role="Name" value="update" />
        <property role="P200801" value="false" />
        <property role="P200802" value="" />
        <node concept="C2008" id="pU13" role="R200852">
          <property role="Name" value="info" />
          <property role="P200801" value="false" />
          <property role="P200802" value="" />
          <node concept="C2008" id="pU12" role="R200852">
            <property role="Name" value="*" />
            <property role="P200801" value="false" />
            <property role="P200802" value="" />
            <node concept="C2009" id="pU11" role="R200851">
              <property role="Name" value="*" />
              <property role="P200901" value="any" />
              <property role="P200902" value="false" />
              <property role="P200903" value="Configuration field to update; unknown fields are preserved." />
            </node>
          </node>
        </node>
        <node concept="C2008" id="pU16" role="R200852">
          <property role="Name" value="remote" />
          <property role="P200801" value="false" />
          <property role="P200802" value="" />
          <node concept="C2008" id="pU15" role="R200852">
            <property role="Name" value="*" />
            <property role="P200801" value="false" />
            <property role="P200802" value="" />
            <node concept="C2009" id="pU14" role="R200851">
              <property role="Name" value="*" />
              <property role="P200901" value="any" />
              <property role="P200902" value="false" />
              <property role="P200903" value="Configuration field to update; unknown fields are preserved." />
            </node>
          </node>
        </node>
        <node concept="C2008" id="pU19" role="R200852">
          <property role="Name" value="required" />
          <property role="P200801" value="false" />
          <property role="P200802" value="" />
          <node concept="C2008" id="pU18" role="R200852">
            <property role="Name" value="*" />
            <property role="P200801" value="false" />
            <property role="P200802" value="" />
            <node concept="C2009" id="pU17" role="R200851">
              <property role="Name" value="*" />
              <property role="P200901" value="any" />
              <property role="P200902" value="false" />
              <property role="P200903" value="Configuration field to update; unknown fields are preserved." />
            </node>
          </node>
        </node>
        <node concept="C2008" id="pU22" role="R200852">
          <property role="Name" value="preset" />
          <property role="P200801" value="false" />
          <property role="P200802" value="" />
          <node concept="C2008" id="pU21" role="R200852">
            <property role="Name" value="*" />
            <property role="P200801" value="false" />
            <property role="P200802" value="" />
            <node concept="C2009" id="pU20" role="R200851">
              <property role="Name" value="*" />
              <property role="P200901" value="any" />
              <property role="P200902" value="false" />
              <property role="P200903" value="Configuration field to update; unknown fields are preserved." />
            </node>
          </node>
        </node>
      </node>
    </node>
    <node concept="C2007" id="cgA" role="R200156">
      <property role="Name" value="version" />
      <property role="P200701" value="Modpack version config&#10;Usually stored as &lt;DLC&gt;/info/version.dc" />
      <node concept="C2011" id="cgB" role="R200751">
        <property role="P201101" value="**/version.dc" />
      </node>
      <node concept="C2008" id="cgC" role="R200752">
        <property role="Name" value="@root" />
        <property role="P200801" value="false" />
        <property role="P200802" value="" />
        <node concept="C2009" id="cgD" role="R200851">
          <property role="Name" value="base" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Current modpack base version." />
        </node>
        <node concept="C2009" id="cgE" role="R200851">
          <property role="Name" value="dlc" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Current DLC version." />
        </node>
      </node>
    </node>
    <node concept="C2007" id="cgF" role="R200156">
      <property role="Name" value="config" />
      <property role="P200701" value="DLC Manager config&#10;Usually stored as config/dlc_manager/config.dc" />
      <node concept="C2011" id="cgG" role="R200751">
        <property role="P201101" value="**/config.dc" />
      </node>
      <node concept="C2008" id="cgH" role="R200752">
        <property role="Name" value="paths" />
        <property role="P200801" value="false" />
        <property role="P200802" value="" />
        <node concept="C2009" id="cgI" role="R200851">
          <property role="Name" value="dlcRoot" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="DLC root directory." />
        </node>
        <node concept="C2009" id="cgJ" role="R200851">
          <property role="Name" value="taczRoot" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="TACZ pack directory name." />
        </node>
        <node concept="C2009" id="cgK" role="R200851">
          <property role="Name" value="resourcepackRoot" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Resource pack directory name." />
        </node>
        <node concept="C2009" id="cgL" role="R200851">
          <property role="Name" value="datapackRoot" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Data pack directory name." />
        </node>
      </node>
      <node concept="C2008" id="cgM" role="R200752">
        <property role="Name" value="ui" />
        <property role="P200801" value="false" />
        <property role="P200802" value="" />
        <node concept="C2009" id="cgN" role="R200851">
          <property role="Name" value="isEnableAllButtonShown" />
          <property role="P200901" value="boolean" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Whether to show the Enable/Disable All button." />
        </node>
        <node concept="C2009" id="cgO" role="R200851">
          <property role="Name" value="isConfigButtonShown" />
          <property role="P200901" value="boolean" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Whether to show the config button." />
        </node>
        <node concept="C2009" id="cgP" role="R200851">
          <property role="Name" value="isUButtomShown" />
          <property role="P200901" value="boolean" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Whether to show the U button." />
        </node>
        <node concept="C2009" id="cgQ" role="R200851">
          <property role="Name" value="isUpdateManagerOn" />
          <property role="P200901" value="boolean" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Whether to enable the update manager." />
        </node>
        <node concept="C2009" id="cgR" role="R200851">
          <property role="Name" value="isSelfUpdateAutoCheckOn" />
          <property role="P200901" value="boolean" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Whether to auto-check DLC Manager updates." />
        </node>
        <node concept="C2009" id="cgS" role="R200851">
          <property role="Name" value="isPresetFuncOn" />
          <property role="P200901" value="boolean" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Whether to enable presets." />
        </node>
        <node concept="C2009" id="cgT" role="R200851">
          <property role="Name" value="isPackFuncOn" />
          <property role="P200901" value="boolean" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Whether to enable DLC Packs." />
        </node>
        <node concept="C2009" id="cgU" role="R200851">
          <property role="Name" value="isPButtomShown" />
          <property role="P200901" value="boolean" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Whether to show the P button." />
        </node>
        <node concept="C2009" id="cgV" role="R200851">
          <property role="Name" value="ShowOnFirstRun" />
          <property role="P200901" value="boolean" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Whether to open DLC Manager automatically on first run." />
        </node>
        <node concept="C2009" id="cgW" role="R200851">
          <property role="Name" value="WaitingTime" />
          <property role="P200901" value="number" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Alarm confirmation wait time." />
          <node concept="C2010" id="cgX" role="R200952">
            <property role="Name" value="min" />
            <property role="P201001" value="0" />
          </node>
        </node>
        <node concept="C2009" id="cgY" role="R200851">
          <property role="Name" value="serverSupport" />
          <property role="P200901" value="boolean" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Whether to show the server manager entry." />
        </node>
        <node concept="C2009" id="cgZ" role="R200851">
          <property role="Name" value="apiEnabled" />
          <property role="P200901" value="boolean" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Whether to enable the local API." />
        </node>
        <node concept="C2009" id="ch0" role="R200851">
          <property role="Name" value="apiPort" />
          <property role="P200901" value="number" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Local API port." />
          <node concept="C2010" id="ch1" role="R200952">
            <property role="Name" value="min" />
            <property role="P201001" value="0" />
          </node>
        </node>
        <node concept="C2009" id="ch2" role="R200851">
          <property role="Name" value="apiToken" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Local API token." />
        </node>
        <node concept="C2009" id="ch3" role="R200851">
          <property role="Name" value="noticeDisplayMode" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Information display mode." />
        </node>
        <node concept="C2009" id="ch4" role="R200851">
          <property role="Name" value="noticeButtonAnchor" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Main menu information button anchor." />
        </node>
        <node concept="C2009" id="ch5" role="R200851">
          <property role="Name" value="noticeButtonOffsetX" />
          <property role="P200901" value="number" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Main menu information button offset." />
          <node concept="C2010" id="ch6" role="R200952">
            <property role="Name" value="min" />
            <property role="P201001" value="0" />
          </node>
        </node>
        <node concept="C2009" id="ch7" role="R200851">
          <property role="Name" value="noticeButtonOffsetY" />
          <property role="P200901" value="number" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Main menu information button offset." />
          <node concept="C2010" id="ch8" role="R200952">
            <property role="Name" value="min" />
            <property role="P201001" value="0" />
          </node>
        </node>
        <node concept="C2009" id="ch9" role="R200851">
          <property role="Name" value="noticePanelAnchor" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Main menu information panel anchor." />
        </node>
        <node concept="C2009" id="cha" role="R200851">
          <property role="Name" value="noticePanelOffsetX" />
          <property role="P200901" value="number" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Main menu information panel offset." />
          <node concept="C2010" id="chb" role="R200952">
            <property role="Name" value="min" />
            <property role="P201001" value="0" />
          </node>
        </node>
        <node concept="C2009" id="chc" role="R200851">
          <property role="Name" value="noticePanelOffsetY" />
          <property role="P200901" value="number" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Main menu information panel offset." />
          <node concept="C2010" id="chd" role="R200952">
            <property role="Name" value="min" />
            <property role="P201001" value="0" />
          </node>
        </node>
        <node concept="C2009" id="che" role="R200851">
          <property role="Name" value="noticePanelWidth" />
          <property role="P200901" value="number" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Main menu information panel size." />
          <node concept="C2010" id="chf" role="R200952">
            <property role="Name" value="min" />
            <property role="P201001" value="0" />
          </node>
        </node>
        <node concept="C2009" id="chg" role="R200851">
          <property role="Name" value="noticePanelHeight" />
          <property role="P200901" value="number" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Main menu information panel size." />
          <node concept="C2010" id="chh" role="R200952">
            <property role="Name" value="min" />
            <property role="P201001" value="0" />
          </node>
        </node>
        <node concept="C2009" id="chi" role="R200851">
          <property role="Name" value="titleAnchor" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="DLC Manager main menu button anchor." />
        </node>
        <node concept="C2009" id="chj" role="R200851">
          <property role="Name" value="titleOffsetX" />
          <property role="P200901" value="number" />
          <property role="P200902" value="false" />
          <property role="P200903" value="DLC Manager main menu button offset." />
          <node concept="C2010" id="chk" role="R200952">
            <property role="Name" value="min" />
            <property role="P201001" value="0" />
          </node>
        </node>
        <node concept="C2009" id="chl" role="R200851">
          <property role="Name" value="titleOffsetY" />
          <property role="P200901" value="number" />
          <property role="P200902" value="false" />
          <property role="P200903" value="DLC Manager main menu button offset." />
          <node concept="C2010" id="chm" role="R200952">
            <property role="Name" value="min" />
            <property role="P201001" value="0" />
          </node>
        </node>
        <node concept="C2009" id="chn" role="R200851">
          <property role="Name" value="titleUpdateAnchor" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="U button anchor." />
        </node>
        <node concept="C2009" id="cho" role="R200851">
          <property role="Name" value="titleUpdateOffsetX" />
          <property role="P200901" value="number" />
          <property role="P200902" value="false" />
          <property role="P200903" value="U button offset." />
          <node concept="C2010" id="chp" role="R200952">
            <property role="Name" value="min" />
            <property role="P201001" value="0" />
          </node>
        </node>
        <node concept="C2009" id="chq" role="R200851">
          <property role="Name" value="titleUpdateOffsetY" />
          <property role="P200901" value="number" />
          <property role="P200902" value="false" />
          <property role="P200903" value="U button offset." />
          <node concept="C2010" id="chr" role="R200952">
            <property role="Name" value="min" />
            <property role="P201001" value="0" />
          </node>
        </node>
      </node>
      <node concept="C2008" id="chs" role="R200752">
        <property role="Name" value="modpack" />
        <property role="P200801" value="false" />
        <property role="P200802" value="" />
        <node concept="C2009" id="cht" role="R200851">
          <property role="Name" value="modpack" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Current modpack identifier, used for DLC Pack modpack condition checks." />
        </node>
        <node concept="C2009" id="chu" role="R200851">
          <property role="Name" value="modpackUrl" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Modpack homepage link." />
        </node>
        <node concept="C2009" id="chv" role="R200851">
          <property role="Name" value="updateGitRepo" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Modpack version repository link." />
        </node>
      </node>
      <node concept="C2008" id="chw" role="R200752">
        <property role="Name" value="downloads" />
        <property role="P200801" value="false" />
        <property role="P200802" value="" />
        <node concept="C2009" id="chx" role="R200851">
          <property role="Name" value="maxConcurrentDownloads" />
          <property role="P200901" value="number" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Maximum concurrent download tasks." />
          <node concept="C2010" id="chy" role="R200952">
            <property role="Name" value="min" />
            <property role="P201001" value="0" />
          </node>
        </node>
        <node concept="C2009" id="chz" role="R200851">
          <property role="Name" value="maxSegmentsPerFile" />
          <property role="P200901" value="number" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Maximum segments per file." />
          <node concept="C2010" id="ch$" role="R200952">
            <property role="Name" value="min" />
            <property role="P201001" value="0" />
          </node>
        </node>
        <node concept="C2009" id="ch_" role="R200851">
          <property role="Name" value="minSegmentMB" />
          <property role="P200901" value="number" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Minimum segment size for segmented downloads." />
          <node concept="C2010" id="chA" role="R200952">
            <property role="Name" value="min" />
            <property role="P201001" value="0" />
          </node>
        </node>
        <node concept="C2009" id="chB" role="R200851">
          <property role="Name" value="maxRetryCount" />
          <property role="P200901" value="number" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Maximum download retry attempts." />
          <node concept="C2010" id="chC" role="R200952">
            <property role="Name" value="min" />
            <property role="P201001" value="0" />
          </node>
        </node>
        <node concept="C2009" id="chD" role="R200851">
          <property role="Name" value="connectTimeoutSeconds" />
          <property role="P200901" value="number" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Connection timeout." />
          <node concept="C2010" id="chE" role="R200952">
            <property role="Name" value="min" />
            <property role="P201001" value="0" />
          </node>
        </node>
        <node concept="C2009" id="chF" role="R200851">
          <property role="Name" value="taskTimeoutSeconds" />
          <property role="P200901" value="number" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Single task timeout." />
          <node concept="C2010" id="chG" role="R200952">
            <property role="Name" value="min" />
            <property role="P201001" value="0" />
          </node>
        </node>
        <node concept="C2009" id="chH" role="R200851">
          <property role="Name" value="downloadMirrorMode" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Download mirror mode." />
          <node concept="C2011" id="chI" role="R200951">
            <property role="P201101" value="smart_precheck" />
          </node>
          <node concept="C2011" id="chJ" role="R200951">
            <property role="P201101" value="smart_official_first" />
          </node>
          <node concept="C2011" id="chK" role="R200951">
            <property role="P201101" value="smart_mirror_first" />
          </node>
          <node concept="C2011" id="chL" role="R200951">
            <property role="P201101" value="official_only" />
          </node>
          <node concept="C2011" id="chM" role="R200951">
            <property role="P201101" value="mirror_only" />
          </node>
          <node concept="C2011" id="chN" role="R200951">
            <property role="P201101" value="official_first" />
          </node>
          <node concept="C2011" id="chO" role="R200951">
            <property role="P201101" value="mirror_first" />
          </node>
        </node>
        <node concept="C2009" id="chP" role="R200851">
          <property role="Name" value="downloadMirrorProbeCount" />
          <property role="P200901" value="number" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Download source probe count." />
          <node concept="C2010" id="chQ" role="R200952">
            <property role="Name" value="min" />
            <property role="P201001" value="0" />
          </node>
        </node>
        <node concept="C2009" id="chR" role="R200851">
          <property role="Name" value="downloadMirrorProbeStrategy" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Download source probe selection strategy." />
          <node concept="C2011" id="chS" role="R200951">
            <property role="P201101" value="single_best" />
          </node>
          <node concept="C2011" id="chT" role="R200951">
            <property role="P201101" value="best_of_three" />
          </node>
        </node>
      </node>
    </node>
    <node concept="C2007" id="chU" role="R200156">
      <property role="Name" value="pack" />
      <property role="P200701" value="DLC Pack config&#10;Stored as metadata/pack.dc" />
      <node concept="C2011" id="chV" role="R200751">
        <property role="P201101" value="**/pack.dc" />
      </node>
      <node concept="C2008" id="chW" role="R200752">
        <property role="Name" value="basic" />
        <property role="P200801" value="false" />
        <property role="P200802" value="" />
        <node concept="C2009" id="chX" role="R200851">
          <property role="Name" value="identifier" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="DLC Pack identifier." />
        </node>
        <node concept="C2009" id="chY" role="R200851">
          <property role="Name" value="version" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="DLC Pack version." />
        </node>
        <node concept="C2009" id="chZ" role="R200851">
          <property role="Name" value="platform" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Target side. Use client, server, or universal." />
          <node concept="C2011" id="ci0" role="R200951">
            <property role="P201101" value="universal" />
          </node>
          <node concept="C2011" id="ci1" role="R200951">
            <property role="P201101" value="client" />
          </node>
          <node concept="C2011" id="ci2" role="R200951">
            <property role="P201101" value="server" />
          </node>
        </node>
        <node concept="C2009" id="ci3" role="R200851">
          <property role="Name" value="gameVersion" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Target game version. Empty means any game version." />
        </node>
        <node concept="C2009" id="ci4" role="R200851">
          <property role="Name" value="modLoader" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Target mod loader. Empty means any loader." />
        </node>
        <node concept="C2009" id="ci5" role="R200851">
          <property role="Name" value="author" />
          <property role="P200901" value="array" />
          <property role="P200902" value="false" />
          <property role="P200903" value="DLC Pack author list." />
        </node>
      </node>
      <node concept="C2008" id="ci6" role="R200752">
        <property role="Name" value="modpack" />
        <property role="P200801" value="false" />
        <property role="P200802" value="" />
        <node concept="C2009" id="ci7" role="R200851">
          <property role="Name" value="modpack" />
          <property role="P200901" value="array" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Allowed modpack identifiers. Empty means any modpack." />
        </node>
        <node concept="C2009" id="ci8" role="R200851">
          <property role="Name" value="base" />
          <property role="P200901" value="array" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Allowed base versions. Supports exact versions, x wildcards, and ranges. Empty means any version." />
        </node>
        <node concept="C2009" id="ci9" role="R200851">
          <property role="Name" value="dlc" />
          <property role="P200901" value="array" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Allowed DLC versions. Supports exact versions, x wildcards, and ranges. Empty means any version." />
        </node>
      </node>
      <node concept="C2008" id="cia" role="R200752">
        <property role="Name" value="links" />
        <property role="P200801" value="false" />
        <property role="P200802" value="" />
        <node concept="C2009" id="cib" role="R200851">
          <property role="Name" value="homepage" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Global DLC Pack homepage link." />
        </node>
        <node concept="C2009" id="cic" role="R200851">
          <property role="Name" value="issue" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Global DLC Pack issue link." />
        </node>
        <node concept="C2009" id="cid" role="R200851">
          <property role="Name" value="sponsor" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Global DLC Pack sponsor link." />
        </node>
      </node>
      <node concept="C2008" id="cie" role="R200752">
        <property role="Name" value="dependencies" />
        <property role="P200801" value="false" />
        <property role="P200802" value="" />
        <node concept="C2009" id="cif" role="R200851">
          <property role="Name" value="*" />
          <property role="P200901" value="object|string|array" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Dependency identifier or relative path list." />
        </node>
      </node>
      <node concept="C2008" id="cig" role="R200752">
        <property role="Name" value="conflicts" />
        <property role="P200801" value="false" />
        <property role="P200802" value="" />
        <node concept="C2009" id="cih" role="R200851">
          <property role="Name" value="*" />
          <property role="P200901" value="object|string|array" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Conflict identifier or relative path list." />
        </node>
      </node>
      <node concept="C2008" id="cii" role="R200752">
        <property role="Name" value="DisplayName" />
        <property role="P200801" value="false" />
        <property role="P200802" value="" />
        <node concept="C2009" id="cij" role="R200851">
          <property role="Name" value="*" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Localized value keyed by language code." />
        </node>
      </node>
      <node concept="C2008" id="cik" role="R200752">
        <property role="Name" value="description" />
        <property role="P200801" value="false" />
        <property role="P200802" value="" />
        <node concept="C2009" id="cil" role="R200851">
          <property role="Name" value="*" />
          <property role="P200901" value="string" />
          <property role="P200902" value="false" />
          <property role="P200903" value="Localized value keyed by language code." />
        </node>
      </node>
    </node>
    <node concept="C2007" id="cim" role="R200156">
      <property role="Name" value="strict-example" />
      <property role="P200701" value="Example of explicit required categories and fields." />
      <node concept="C2008" id="cin" role="R200752">
        <property role="Name" value="basic" />
        <property role="P200801" value="true" />
        <property role="P200802" value="Basic metadata." />
        <node concept="C2009" id="cio" role="R200851">
          <property role="Name" value="identifier" />
          <property role="P200901" value="string" />
          <property role="P200902" value="true" />
          <property role="P200903" value="Explicit identifier." />
          <node concept="C2010" id="cip" role="R200952">
            <property role="Name" value="minLength" />
            <property role="P201001" value="1" />
          </node>
        </node>
      </node>
    </node>
  </node>
</model>