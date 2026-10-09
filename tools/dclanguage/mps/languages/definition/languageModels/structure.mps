<?xml version='1.0' encoding='UTF-8'?>
<model ref="r:7c669566-3388-4f44-923a-c8a767fd751a(com.cozooo.dclanguage.definition.structure)">
  <persistence version="9" />
  <languages>
    <devkit ref="78434eb8-b0e5-444b-850d-e7c4ad2da9ab(jetbrains.mps.devkit.aspect.structure)" />
  </languages>
  <imports>
    <import index="tpck" ref="r:00000000-0000-4000-0000-011c89590288(jetbrains.mps.lang.core.structure)" implicit="true" />
  </imports>
  <registry>
    <language id="c72da2b9-7cce-4447-8389-f407dc1158b7" name="jetbrains.mps.lang.structure">
      <concept id="1169125787135" name="jetbrains.mps.lang.structure.structure.AbstractConceptDeclaration" flags="ig" index="PkWjJ">
        <property id="6714410169261853888" name="conceptId" index="EcuMT" />
        <property id="4628067390765956807" name="final" index="R5$K2" />
        <property id="4628067390765956802" name="abstract" index="R5$K7" />
        <property id="5092175715804935370" name="conceptAlias" index="34LRSv" />
        <child id="1071489727083" name="linkDeclaration" index="1TKVEi" />
        <child id="1071489727084" name="propertyDeclaration" index="1TKVEl" />
      </concept>
      <concept id="1169127622168" name="jetbrains.mps.lang.structure.structure.InterfaceConceptReference" flags="ig" index="PrWs8">
        <reference id="1169127628841" name="intfc" index="PrY4T" />
      </concept>
      <concept id="1071489090640" name="jetbrains.mps.lang.structure.structure.ConceptDeclaration" flags="ig" index="1TIwiD">
        <property id="1096454100552" name="rootable" index="19KtqR" />
        <reference id="1071489389519" name="extends" index="1TJDcQ" />
        <child id="1169129564478" name="implements" index="PzmwI" />
      </concept>
      <concept id="1071489288299" name="jetbrains.mps.lang.structure.structure.PropertyDeclaration" flags="ig" index="1TJgyi">
        <property id="241647608299431129" name="propertyId" index="IQ2nx" />
        <reference id="1082985295845" name="dataType" index="AX2Wp" />
      </concept>
      <concept id="1071489288298" name="jetbrains.mps.lang.structure.structure.LinkDeclaration" flags="ig" index="1TJgyj">
        <property id="1071599776563" name="role" index="20kJfa" />
        <property id="1071599893252" name="sourceCardinality" index="20lbJX" />
        <property id="1071599937831" name="metaClass" index="20lmBu" />
        <property id="241647608299431140" name="linkId" index="IQ2ns" />
        <reference id="1071599976176" name="target" index="20lvS9" />
      </concept>
    </language>
    <language id="ceab5195-25ea-4f22-9b92-103b95ca8c0c" name="jetbrains.mps.lang.core">
      <concept id="1169194658468" name="jetbrains.mps.lang.core.structure.INamedConcept" flags="ngI" index="TrEIO">
        <property id="1169194664001" name="name" index="TrG5h" />
      </concept>
    </language>
  </registry>
  <node concept="1TIwiD" id="vh">
    <property role="TrG5h" value="Definition" />
    <property role="EcuMT" value="2001" />
    <property role="19KtqR" value="true" />
    <ref role="1TJDcQ" to="tpck:gw2VY9q" resolve="BaseConcept" />
    <node concept="PrWs8" id="KS7" role="PzmwI">
      <ref role="PrY4T" to="tpck:h0TrEE$" resolve="INamedConcept" />
    </node>
    <node concept="1TJgyi" id="KQ_" role="1TKVEl">
      <property role="TrG5h" value="formatVersion" />
      <property role="IQ2nx" value="200101" />
      <ref role="AX2Wp" to="tpck:fKAQMTA" resolve="integer" />
    </node>
    <node concept="1TJgyj" id="KRn" role="1TKVEi">
      <property role="20lmBu" value="fLJjDmT/aggregation" />
      <property role="20kJfa" value="tokens" />
      <property role="20lbJX" value="fLJekj5/0..n" />
      <property role="IQ2ns" value="200151" />
      <ref role="20lvS9" node="vi" resolve="SyntaxToken" />
    </node>
    <node concept="1TJgyj" id="KRo" role="1TKVEi">
      <property role="20lmBu" value="fLJjDmT/aggregation" />
      <property role="20kJfa" value="strings" />
      <property role="20lbJX" value="fLJekj5/0..n" />
      <property role="IQ2ns" value="200152" />
      <ref role="20lvS9" node="vj" resolve="StringRule" />
    </node>
    <node concept="1TJgyj" id="KRp" role="1TKVEi">
      <property role="20lmBu" value="fLJjDmT/aggregation" />
      <property role="20kJfa" value="escapes" />
      <property role="20lbJX" value="fLJekj5/0..n" />
      <property role="IQ2ns" value="200153" />
      <ref role="20lvS9" node="vk" resolve="EscapeRule" />
    </node>
    <node concept="1TJgyj" id="KRq" role="1TKVEi">
      <property role="20lmBu" value="fLJjDmT/aggregation" />
      <property role="20kJfa" value="valueTypes" />
      <property role="20lbJX" value="fLJekj5/0..n" />
      <property role="IQ2ns" value="200154" />
      <ref role="20lvS9" node="vl" resolve="ValueType" />
    </node>
    <node concept="1TJgyj" id="KRr" role="1TKVEi">
      <property role="20lmBu" value="fLJjDmT/aggregation" />
      <property role="20kJfa" value="policies" />
      <property role="20lbJX" value="fLJekj5/0..n" />
      <property role="IQ2ns" value="200155" />
      <ref role="20lvS9" node="vm" resolve="Policy" />
    </node>
    <node concept="1TJgyj" id="KRs" role="1TKVEi">
      <property role="20lmBu" value="fLJjDmT/aggregation" />
      <property role="20kJfa" value="profiles" />
      <property role="20lbJX" value="fLJekj5/0..n" />
      <property role="IQ2ns" value="200156" />
      <ref role="20lvS9" node="vn" resolve="SchemaProfile" />
    </node>
    <node concept="1TJgyj" id="KRt" role="1TKVEi">
      <property role="20lmBu" value="fLJjDmT/aggregation" />
      <property role="20kJfa" value="literals" />
      <property role="20lbJX" value="fLJekj5/0..n" />
      <property role="IQ2ns" value="200157" />
      <ref role="20lvS9" node="vs" resolve="LiteralRule" />
    </node>
    <node concept="1TJgyj" id="KRu" role="1TKVEi">
      <property role="20lmBu" value="fLJjDmT/aggregation" />
      <property role="20kJfa" value="numbers" />
      <property role="20lbJX" value="fLJekj5/0..n" />
      <property role="IQ2ns" value="200158" />
      <ref role="20lvS9" node="vt" resolve="NumberRule" />
    </node>
    <node concept="1TJgyj" id="KRv" role="1TKVEi">
      <property role="20lmBu" value="fLJjDmT/aggregation" />
      <property role="20kJfa" value="names" />
      <property role="20lbJX" value="fLJekj5/0..n" />
      <property role="IQ2ns" value="200159" />
      <ref role="20lvS9" node="vu" resolve="NameRule" />
    </node>
  </node>
  <node concept="1TIwiD" id="vi">
    <property role="TrG5h" value="SyntaxToken" />
    <property role="EcuMT" value="2002" />
    <ref role="1TJDcQ" to="tpck:gw2VY9q" resolve="BaseConcept" />
    <node concept="PrWs8" id="KTF" role="PzmwI">
      <ref role="PrY4T" to="tpck:h0TrEE$" resolve="INamedConcept" />
    </node>
    <node concept="1TJgyi" id="KS9" role="1TKVEl">
      <property role="TrG5h" value="value" />
      <property role="IQ2nx" value="200201" />
      <ref role="AX2Wp" to="tpck:fKAOsGN" resolve="string" />
    </node>
  </node>
  <node concept="1TIwiD" id="vj">
    <property role="TrG5h" value="StringRule" />
    <property role="EcuMT" value="2003" />
    <ref role="1TJDcQ" to="tpck:gw2VY9q" resolve="BaseConcept" />
    <node concept="PrWs8" id="KVf" role="PzmwI">
      <ref role="PrY4T" to="tpck:h0TrEE$" resolve="INamedConcept" />
    </node>
    <node concept="1TJgyi" id="KTH" role="1TKVEl">
      <property role="TrG5h" value="delimiter" />
      <property role="IQ2nx" value="200301" />
      <ref role="AX2Wp" to="tpck:fKAOsGN" resolve="string" />
    </node>
    <node concept="1TJgyi" id="KTI" role="1TKVEl">
      <property role="TrG5h" value="multiline" />
      <property role="IQ2nx" value="200302" />
      <ref role="AX2Wp" to="tpck:fKAQMTB" resolve="boolean" />
    </node>
    <node concept="1TJgyi" id="KTJ" role="1TKVEl">
      <property role="TrG5h" value="escaped" />
      <property role="IQ2nx" value="200303" />
      <ref role="AX2Wp" to="tpck:fKAQMTB" resolve="boolean" />
    </node>
  </node>
  <node concept="1TIwiD" id="vk">
    <property role="TrG5h" value="EscapeRule" />
    <property role="EcuMT" value="2004" />
    <ref role="1TJDcQ" to="tpck:gw2VY9q" resolve="BaseConcept" />
    <node concept="PrWs8" id="KWN" role="PzmwI">
      <ref role="PrY4T" to="tpck:h0TrEE$" resolve="INamedConcept" />
    </node>
    <node concept="1TJgyi" id="KVh" role="1TKVEl">
      <property role="TrG5h" value="value" />
      <property role="IQ2nx" value="200401" />
      <ref role="AX2Wp" to="tpck:fKAOsGN" resolve="string" />
    </node>
  </node>
  <node concept="1TIwiD" id="vl">
    <property role="TrG5h" value="ValueType" />
    <property role="EcuMT" value="2005" />
    <ref role="1TJDcQ" to="tpck:gw2VY9q" resolve="BaseConcept" />
    <node concept="PrWs8" id="KYn" role="PzmwI">
      <ref role="PrY4T" to="tpck:h0TrEE$" resolve="INamedConcept" />
    </node>
    <node concept="1TJgyi" id="KWP" role="1TKVEl">
      <property role="TrG5h" value="description" />
      <property role="IQ2nx" value="200501" />
      <ref role="AX2Wp" to="tpck:fKAOsGN" resolve="string" />
    </node>
  </node>
  <node concept="1TIwiD" id="vm">
    <property role="TrG5h" value="Policy" />
    <property role="EcuMT" value="2006" />
    <ref role="1TJDcQ" to="tpck:gw2VY9q" resolve="BaseConcept" />
    <node concept="PrWs8" id="KZV" role="PzmwI">
      <ref role="PrY4T" to="tpck:h0TrEE$" resolve="INamedConcept" />
    </node>
    <node concept="1TJgyi" id="KYp" role="1TKVEl">
      <property role="TrG5h" value="value" />
      <property role="IQ2nx" value="200601" />
      <ref role="AX2Wp" to="tpck:fKAOsGN" resolve="string" />
    </node>
  </node>
  <node concept="1TIwiD" id="vn">
    <property role="TrG5h" value="SchemaProfile" />
    <property role="EcuMT" value="2007" />
    <ref role="1TJDcQ" to="tpck:gw2VY9q" resolve="BaseConcept" />
    <node concept="PrWs8" id="L1v" role="PzmwI">
      <ref role="PrY4T" to="tpck:h0TrEE$" resolve="INamedConcept" />
    </node>
    <node concept="1TJgyi" id="KZX" role="1TKVEl">
      <property role="TrG5h" value="description" />
      <property role="IQ2nx" value="200701" />
      <ref role="AX2Wp" to="tpck:fKAOsGN" resolve="string" />
    </node>
    <node concept="1TJgyj" id="L0J" role="1TKVEi">
      <property role="20lmBu" value="fLJjDmT/aggregation" />
      <property role="20kJfa" value="filePatterns" />
      <property role="20lbJX" value="fLJekj5/0..n" />
      <property role="IQ2ns" value="200751" />
      <ref role="20lvS9" node="vr" resolve="StringValue" />
    </node>
    <node concept="1TJgyj" id="L0K" role="1TKVEi">
      <property role="20lmBu" value="fLJjDmT/aggregation" />
      <property role="20kJfa" value="categories" />
      <property role="20lbJX" value="fLJekj5/0..n" />
      <property role="IQ2ns" value="200752" />
      <ref role="20lvS9" node="vo" resolve="Category" />
    </node>
  </node>
  <node concept="1TIwiD" id="vo">
    <property role="TrG5h" value="Category" />
    <property role="EcuMT" value="2008" />
    <ref role="1TJDcQ" to="tpck:gw2VY9q" resolve="BaseConcept" />
    <node concept="PrWs8" id="L33" role="PzmwI">
      <ref role="PrY4T" to="tpck:h0TrEE$" resolve="INamedConcept" />
    </node>
    <node concept="1TJgyi" id="L1x" role="1TKVEl">
      <property role="TrG5h" value="required" />
      <property role="IQ2nx" value="200801" />
      <ref role="AX2Wp" to="tpck:fKAQMTB" resolve="boolean" />
    </node>
    <node concept="1TJgyi" id="L1y" role="1TKVEl">
      <property role="TrG5h" value="description" />
      <property role="IQ2nx" value="200802" />
      <ref role="AX2Wp" to="tpck:fKAOsGN" resolve="string" />
    </node>
    <node concept="1TJgyj" id="L2j" role="1TKVEi">
      <property role="20lmBu" value="fLJjDmT/aggregation" />
      <property role="20kJfa" value="fields" />
      <property role="20lbJX" value="fLJekj5/0..n" />
      <property role="IQ2ns" value="200851" />
      <ref role="20lvS9" node="vp" resolve="Field" />
    </node>
    <node concept="1TJgyj" id="L2k" role="1TKVEi">
      <property role="20lmBu" value="fLJjDmT/aggregation" />
      <property role="20kJfa" value="categories" />
      <property role="20lbJX" value="fLJekj5/0..n" />
      <property role="IQ2ns" value="200852" />
      <ref role="20lvS9" node="vo" resolve="Category" />
    </node>
  </node>
  <node concept="1TIwiD" id="vp">
    <property role="TrG5h" value="Field" />
    <property role="EcuMT" value="2009" />
    <ref role="1TJDcQ" to="tpck:gw2VY9q" resolve="BaseConcept" />
    <node concept="PrWs8" id="L4B" role="PzmwI">
      <ref role="PrY4T" to="tpck:h0TrEE$" resolve="INamedConcept" />
    </node>
    <node concept="1TJgyi" id="L35" role="1TKVEl">
      <property role="TrG5h" value="type" />
      <property role="IQ2nx" value="200901" />
      <ref role="AX2Wp" to="tpck:fKAOsGN" resolve="string" />
    </node>
    <node concept="1TJgyi" id="L36" role="1TKVEl">
      <property role="TrG5h" value="required" />
      <property role="IQ2nx" value="200902" />
      <ref role="AX2Wp" to="tpck:fKAQMTB" resolve="boolean" />
    </node>
    <node concept="1TJgyi" id="L37" role="1TKVEl">
      <property role="TrG5h" value="description" />
      <property role="IQ2nx" value="200903" />
      <ref role="AX2Wp" to="tpck:fKAOsGN" resolve="string" />
    </node>
    <node concept="1TJgyi" id="L38" role="1TKVEl">
      <property role="TrG5h" value="defaultValue" />
      <property role="IQ2nx" value="200904" />
      <ref role="AX2Wp" to="tpck:fKAOsGN" resolve="string" />
    </node>
    <node concept="1TJgyj" id="L3R" role="1TKVEi">
      <property role="20lmBu" value="fLJjDmT/aggregation" />
      <property role="20kJfa" value="enumValues" />
      <property role="20lbJX" value="fLJekj5/0..n" />
      <property role="IQ2ns" value="200951" />
      <ref role="20lvS9" node="vr" resolve="StringValue" />
    </node>
    <node concept="1TJgyj" id="L3S" role="1TKVEi">
      <property role="20lmBu" value="fLJjDmT/aggregation" />
      <property role="20kJfa" value="constraints" />
      <property role="20lbJX" value="fLJekj5/0..n" />
      <property role="IQ2ns" value="200952" />
      <ref role="20lvS9" node="vq" resolve="Constraint" />
    </node>
  </node>
  <node concept="1TIwiD" id="vq">
    <property role="TrG5h" value="Constraint" />
    <property role="EcuMT" value="2010" />
    <ref role="1TJDcQ" to="tpck:gw2VY9q" resolve="BaseConcept" />
    <node concept="PrWs8" id="L6b" role="PzmwI">
      <ref role="PrY4T" to="tpck:h0TrEE$" resolve="INamedConcept" />
    </node>
    <node concept="1TJgyi" id="L4D" role="1TKVEl">
      <property role="TrG5h" value="value" />
      <property role="IQ2nx" value="201001" />
      <ref role="AX2Wp" to="tpck:fKAOsGN" resolve="string" />
    </node>
  </node>
  <node concept="1TIwiD" id="vr">
    <property role="TrG5h" value="StringValue" />
    <property role="EcuMT" value="2011" />
    <ref role="1TJDcQ" to="tpck:gw2VY9q" resolve="BaseConcept" />
    <node concept="PrWs8" id="L7J" role="PzmwI">
      <ref role="PrY4T" to="tpck:h0TrEE$" resolve="INamedConcept" />
    </node>
    <node concept="1TJgyi" id="L6d" role="1TKVEl">
      <property role="TrG5h" value="value" />
      <property role="IQ2nx" value="201101" />
      <ref role="AX2Wp" to="tpck:fKAOsGN" resolve="string" />
    </node>
  </node>
  <node concept="1TIwiD" id="vs">
    <property role="TrG5h" value="LiteralRule" />
    <property role="EcuMT" value="2012" />
    <ref role="1TJDcQ" to="tpck:gw2VY9q" resolve="BaseConcept" />
    <node concept="PrWs8" id="L9j" role="PzmwI">
      <ref role="PrY4T" to="tpck:h0TrEE$" resolve="INamedConcept" />
    </node>
    <node concept="1TJgyi" id="L7L" role="1TKVEl">
      <property role="TrG5h" value="value" />
      <property role="IQ2nx" value="201201" />
      <ref role="AX2Wp" to="tpck:fKAOsGN" resolve="string" />
    </node>
    <node concept="1TJgyi" id="L7M" role="1TKVEl">
      <property role="TrG5h" value="type" />
      <property role="IQ2nx" value="201202" />
      <ref role="AX2Wp" to="tpck:fKAOsGN" resolve="string" />
    </node>
  </node>
  <node concept="1TIwiD" id="vt">
    <property role="TrG5h" value="NumberRule" />
    <property role="EcuMT" value="2013" />
    <ref role="1TJDcQ" to="tpck:gw2VY9q" resolve="BaseConcept" />
    <node concept="PrWs8" id="LaR" role="PzmwI">
      <ref role="PrY4T" to="tpck:h0TrEE$" resolve="INamedConcept" />
    </node>
    <node concept="1TJgyi" id="L9l" role="1TKVEl">
      <property role="TrG5h" value="pattern" />
      <property role="IQ2nx" value="201301" />
      <ref role="AX2Wp" to="tpck:fKAOsGN" resolve="string" />
    </node>
  </node>
  <node concept="1TIwiD" id="vu">
    <property role="TrG5h" value="NameRule" />
    <property role="EcuMT" value="2014" />
    <ref role="1TJDcQ" to="tpck:gw2VY9q" resolve="BaseConcept" />
    <node concept="PrWs8" id="Lcr" role="PzmwI">
      <ref role="PrY4T" to="tpck:h0TrEE$" resolve="INamedConcept" />
    </node>
    <node concept="1TJgyi" id="LaT" role="1TKVEl">
      <property role="TrG5h" value="pattern" />
      <property role="IQ2nx" value="201401" />
      <ref role="AX2Wp" to="tpck:fKAOsGN" resolve="string" />
    </node>
  </node>
</model>