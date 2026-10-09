<?xml version='1.0' encoding='UTF-8'?>
<model ref="r:2de281de-6087-49df-acad-52ea3a52d028(com.cozooo.dclanguage.mps.structure)">
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
  <node concept="1TIwiD" id="fD">
    <property role="TrG5h" value="Document" />
    <property role="EcuMT" value="1001" />
    <property role="19KtqR" value="true" />
    <ref role="1TJDcQ" to="tpck:gw2VY9q" resolve="BaseConcept" />
    <node concept="PrWs8" id="vh" role="PzmwI">
      <ref role="PrY4T" to="tpck:h0TrEE$" resolve="INamedConcept" />
    </node>
    <node concept="1TJgyj" id="iL" role="1TKVEi">
      <property role="20lmBu" value="fLJjDmT/aggregation" />
      <property role="20kJfa" value="members" />
      <property role="20lbJX" value="fLJekj5/0..n" />
      <property role="IQ2ns" value="1201" />
      <ref role="20lvS9" node="fE" resolve="Member" />
    </node>
  </node>
  <node concept="1TIwiD" id="fE">
    <property role="TrG5h" value="Member" />
    <property role="EcuMT" value="1002" />
    <ref role="1TJDcQ" to="tpck:gw2VY9q" resolve="BaseConcept" />
    <node concept="PrWs8" id="vi" role="PzmwI">
      <ref role="PrY4T" to="tpck:h0TrEE$" resolve="INamedConcept" />
    </node>
    <node concept="1TJgyj" id="iM" role="1TKVEi">
      <property role="20lmBu" value="fLJjDmT/aggregation" />
      <property role="20kJfa" value="value" />
      <property role="20lbJX" value="fLJekj4/1" />
      <property role="IQ2ns" value="1202" />
      <ref role="20lvS9" node="fF" resolve="Value" />
    </node>
  </node>
  <node concept="1TIwiD" id="fF">
    <property role="TrG5h" value="Value" />
    <property role="EcuMT" value="1003" />
    <property role="R5$K7" value="true" />
    <ref role="1TJDcQ" to="tpck:gw2VY9q" resolve="BaseConcept" />
  </node>
  <node concept="1TIwiD" id="fG">
    <property role="TrG5h" value="ObjectValue" />
    <property role="EcuMT" value="1004" />
    <ref role="1TJDcQ" node="fF" resolve="Value" />
    <node concept="1TJgyj" id="iO" role="1TKVEi">
      <property role="20lmBu" value="fLJjDmT/aggregation" />
      <property role="20kJfa" value="members" />
      <property role="20lbJX" value="fLJekj5/0..n" />
      <property role="IQ2ns" value="1204" />
      <ref role="20lvS9" node="fE" resolve="Member" />
    </node>
    <property role="34LRSv" value="object" />
  </node>
  <node concept="1TIwiD" id="fH">
    <property role="TrG5h" value="ArrayValue" />
    <property role="EcuMT" value="1005" />
    <ref role="1TJDcQ" node="fF" resolve="Value" />
    <node concept="1TJgyj" id="iP" role="1TKVEi">
      <property role="20lmBu" value="fLJjDmT/aggregation" />
      <property role="20kJfa" value="values" />
      <property role="20lbJX" value="fLJekj5/0..n" />
      <property role="IQ2ns" value="1205" />
      <ref role="20lvS9" node="fF" resolve="Value" />
    </node>
    <property role="34LRSv" value="array" />
  </node>
  <node concept="1TIwiD" id="fI">
    <property role="TrG5h" value="ScalarValue" />
    <property role="EcuMT" value="1006" />
    <ref role="1TJDcQ" node="fF" resolve="Value" />
    <node concept="1TJgyi" id="hi" role="1TKVEl">
      <property role="TrG5h" value="text" />
      <property role="IQ2nx" value="1106" />
      <ref role="AX2Wp" to="tpck:fKAOsGN" resolve="string" />
    </node>
    <property role="34LRSv" value="value" />
  </node>
</model>