package org.batfish.datamodel.answers;

import static com.google.common.base.MoreObjects.firstNonNull;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;
import java.io.Serializable;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.batfish.common.BatfishException;
import org.batfish.common.ErrorDetails;
import org.batfish.common.Warnings;
import org.batfish.datamodel.DefinedStructureInfo;
import org.batfish.datamodel.IntegerSpace;
import org.batfish.version.BatfishVersion;

/**
 * Stores information extracted while converting vendor-specific configurations to
 * vendor-independent ones.
 */
public class ConvertConfigurationAnswerElement extends InitStepAnswerElement
    implements Serializable {

  /** A non-default number because we changed the class */
  private static final String PROP_DEFINED_STRUCTURES = "definedStructures";

  private static final String PROP_CONVERT_STATUS = "convertStatus";
  private static final String PROP_ERRORS = "errors";
  private static final String PROP_FILE_MAP = "fileMap";
  private static final String PROP_REFERENCED_STRUCTURES = "referencedStructures";
  private static final String PROP_UNDEFINED_REFERENCES = "undefinedReferences";
  private static final String PROP_VERSION = "version";
  private static final String PROP_WARNINGS = "warnings";

  // This will only be null in legacy objects, which used _failed set instead
  @Nullable private SortedMap<String, ConvertStatus> _convertStatus;

  // filename -> structType -> structName -> info
  @Nonnull
  private SortedMap<String, SortedMap<String, SortedMap<String, DefinedStructureInfo>>>
      _definedStructures;

  /* Map of source filename to generated nodes (e.g. "configs/j1.cfg" -> ["j1_master", "j1_logical_system1"]) */
  @Nonnull private final Multimap<String, String> _fileMap;

  // filename -> structType -> structName -> usage -> lines
  @Nonnull
  private final SortedMap<
          String, SortedMap<String, SortedMap<String, SortedMap<String, IntegerSpace>>>>
      _referencedStructures;

  @Nonnull private SortedMap<String, BatfishException.BatfishStackTrace> _errors;

  @Nonnull private final SortedMap<String, ErrorDetails> _errorDetails;

  // This is just to support legacy objects, before _convertStatus map was used
  @Nullable private Set<String> _failed;

  // filename -> structType -> structName -> usage -> lines
  @Nonnull
  private SortedMap<String, SortedMap<String, SortedMap<String, SortedMap<String, IntegerSpace>>>>
      _undefinedReferences;

  @Nonnull private String _version;

  @Nonnull private SortedMap<String, Warnings> _warnings;

  public ConvertConfigurationAnswerElement() {
    this(null, null, null, null, null, null, null, null, null);
  }

  @VisibleForTesting
  @JsonCreator
  ConvertConfigurationAnswerElement(
      @JsonProperty(PROP_DEFINED_STRUCTURES)
          SortedMap<String, SortedMap<String, SortedMap<String, DefinedStructureInfo>>>
              definedStructures,
      @JsonProperty(PROP_REFERENCED_STRUCTURES)
          SortedMap<String, SortedMap<String, SortedMap<String, SortedMap<String, IntegerSpace>>>>
              referencedstructures,
      @JsonProperty(PROP_CONVERT_STATUS) SortedMap<String, ConvertStatus> convertStatus,
      @JsonProperty(PROP_ERRORS) SortedMap<String, BatfishException.BatfishStackTrace> errors,
      @JsonProperty(PROP_ERROR_DETAILS) SortedMap<String, ErrorDetails> errorDetails,
      @JsonProperty(PROP_UNDEFINED_REFERENCES)
          SortedMap<String, SortedMap<String, SortedMap<String, SortedMap<String, IntegerSpace>>>>
              undefinedReferences,
      @JsonProperty(PROP_VERSION) String version,
      @JsonProperty(PROP_WARNINGS) SortedMap<String, Warnings> warnings,
      @JsonProperty(PROP_FILE_MAP) @Nullable Multimap<String, String> fileMap) {
    _definedStructures = firstNonNull(definedStructures, new TreeMap<>());
    _errors = firstNonNull(errors, new TreeMap<>());
    _errorDetails = firstNonNull(errorDetails, new TreeMap<>());
    _fileMap = firstNonNull(fileMap, TreeMultimap.create());
    _convertStatus = firstNonNull(convertStatus, new TreeMap<>());

    _referencedStructures = firstNonNull(referencedstructures, new TreeMap<>());
    _undefinedReferences = firstNonNull(undefinedReferences, new TreeMap<>());
    _version = firstNonNull(version, BatfishVersion.getVersionStatic());
    _warnings = firstNonNull(warnings, new TreeMap<>());
  }

  @JsonProperty(PROP_DEFINED_STRUCTURES)
  @Nonnull
  public SortedMap<String, SortedMap<String, SortedMap<String, DefinedStructureInfo>>>
      getDefinedStructures() {
    return _definedStructures;
  }

  @JsonIgnore
  public SortedMap<String, ConvertStatus> getConvertStatus() {
    return _convertStatus;
  }

  @VisibleForTesting
  @JsonProperty(PROP_CONVERT_STATUS)
  @Nonnull
  SortedMap<String, ConvertStatus> getConvertStatusProp() {
    if (_convertStatus != null) {
      return ImmutableSortedMap.copyOf(_convertStatus);
    } else if (_failed != null) {
      ImmutableSortedMap.Builder<String, ConvertStatus> map = ImmutableSortedMap.naturalOrder();
      _failed.forEach(n -> map.put(n, ConvertStatus.FAILED));
      return map.build();
    } else {
      return ImmutableSortedMap.of();
    }
  }

  @Override
  @JsonProperty(PROP_ERRORS)
  @Nonnull
  public SortedMap<String, BatfishException.BatfishStackTrace> getErrors() {
    return _errors;
  }

  @Override
  @Nonnull
  public SortedMap<String, ErrorDetails> getErrorDetails() {
    return _errorDetails;
  }

  @JsonProperty(PROP_FILE_MAP)
  @Nonnull
  public Multimap<String, String> getFileMap() {
    return _fileMap;
  }

  @JsonProperty(PROP_REFERENCED_STRUCTURES)
  @Nonnull
  public SortedMap<String, SortedMap<String, SortedMap<String, SortedMap<String, IntegerSpace>>>>
      getReferencedStructures() {
    return _referencedStructures;
  }

  @JsonProperty(PROP_UNDEFINED_REFERENCES)
  @Nonnull
  public SortedMap<String, SortedMap<String, SortedMap<String, SortedMap<String, IntegerSpace>>>>
      getUndefinedReferences() {
    return _undefinedReferences;
  }

  @JsonProperty(PROP_VERSION)
  @Nonnull
  public String getVersion() {
    return _version;
  }

  @Override
  @JsonProperty(PROP_WARNINGS)
  @Nonnull
  public SortedMap<String, Warnings> getWarnings() {
    return _warnings;
  }

  @VisibleForTesting
  @JsonIgnore
  void setConvertStatus(@Nullable SortedMap<String, ConvertStatus> convertStatus) {
    _convertStatus = convertStatus;
  }

  public void setDefinedStructures(
      @Nonnull
          SortedMap<String, SortedMap<String, SortedMap<String, DefinedStructureInfo>>>
              definedStructures) {
    _definedStructures = definedStructures;
  }

  @Override
  public void setErrors(@Nonnull SortedMap<String, BatfishException.BatfishStackTrace> errors) {
    _errors = errors;
  }

  @VisibleForTesting
  @JsonIgnore
  void setFailed(Set<String> failed) {
    _failed = failed;
  }

  public void setUndefinedReferences(
      @Nonnull
          SortedMap<String, SortedMap<String, SortedMap<String, SortedMap<String, IntegerSpace>>>>
              undefinedReferences) {
    _undefinedReferences = undefinedReferences;
  }

  public void setVersion(@Nonnull String version) {
    _version = version;
  }

  @Override
  public void setWarnings(@Nonnull SortedMap<String, Warnings> warnings) {
    _warnings = warnings;
  }
}
