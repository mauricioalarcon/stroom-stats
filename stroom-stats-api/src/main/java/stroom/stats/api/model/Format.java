/*
 * Stroom Stats API
 * APIs for interacting with Stroom Stats.
 *
 * OpenAPI spec version: v1
 * 
 *
 * NOTE: This class is auto generated by the swagger code generator program.
 * https://github.com/swagger-api/swagger-codegen.git
 * Do not edit the class manually.
 */


package stroom.stats.api.model;

import java.util.Objects;
import com.google.gson.annotations.SerializedName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import stroom.stats.api.model.DateTimeFormat;
import stroom.stats.api.model.NumberFormat;

/**
 * Describes the formatting that will be applied to values in a field
 */
@ApiModel(description = "Describes the formatting that will be applied to values in a field")
@javax.annotation.Generated(value = "io.swagger.codegen.languages.JavaClientCodegen", date = "2017-12-19T13:37:48.590Z")
public class Format {
  /**
   * The formatting type to apply
   */
  public enum TypeEnum {
    @SerializedName("GENERAL")
    GENERAL("GENERAL"),
    
    @SerializedName("NUMBER")
    NUMBER("NUMBER"),
    
    @SerializedName("DATE_TIME")
    DATE_TIME("DATE_TIME"),
    
    @SerializedName("TEXT")
    TEXT("TEXT");

    private String value;

    TypeEnum(String value) {
      this.value = value;
    }

    @Override
    public String toString() {
      return String.valueOf(value);
    }
  }

  @SerializedName("type")
  private TypeEnum type = null;

  @SerializedName("numberFormat")
  private NumberFormat numberFormat = null;

  @SerializedName("dateTimeFormat")
  private DateTimeFormat dateTimeFormat = null;

  public Format type(TypeEnum type) {
    this.type = type;
    return this;
  }

   /**
   * The formatting type to apply
   * @return type
  **/
  @ApiModelProperty(example = "NUMBER", required = true, value = "The formatting type to apply")
  public TypeEnum getType() {
    return type;
  }

  public void setType(TypeEnum type) {
    this.type = type;
  }

  public Format numberFormat(NumberFormat numberFormat) {
    this.numberFormat = numberFormat;
    return this;
  }

   /**
   * Get numberFormat
   * @return numberFormat
  **/
  @ApiModelProperty(example = "null", value = "")
  public NumberFormat getNumberFormat() {
    return numberFormat;
  }

  public void setNumberFormat(NumberFormat numberFormat) {
    this.numberFormat = numberFormat;
  }

  public Format dateTimeFormat(DateTimeFormat dateTimeFormat) {
    this.dateTimeFormat = dateTimeFormat;
    return this;
  }

   /**
   * Get dateTimeFormat
   * @return dateTimeFormat
  **/
  @ApiModelProperty(example = "null", value = "")
  public DateTimeFormat getDateTimeFormat() {
    return dateTimeFormat;
  }

  public void setDateTimeFormat(DateTimeFormat dateTimeFormat) {
    this.dateTimeFormat = dateTimeFormat;
  }


  @Override
  public boolean equals(java.lang.Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Format format = (Format) o;
    return Objects.equals(this.type, format.type) &&
        Objects.equals(this.numberFormat, format.numberFormat) &&
        Objects.equals(this.dateTimeFormat, format.dateTimeFormat);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, numberFormat, dateTimeFormat);
  }


  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class Format {\n");
    
    sb.append("    type: ").append(toIndentedString(type)).append("\n");
    sb.append("    numberFormat: ").append(toIndentedString(numberFormat)).append("\n");
    sb.append("    dateTimeFormat: ").append(toIndentedString(dateTimeFormat)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private String toIndentedString(java.lang.Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }
  
}

