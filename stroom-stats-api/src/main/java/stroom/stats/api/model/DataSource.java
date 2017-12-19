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
import java.util.ArrayList;
import java.util.List;
import stroom.stats.api.model.DataSourceField;

/**
 * The definition of a data source, describing the fields available
 */
@ApiModel(description = "The definition of a data source, describing the fields available")
@javax.annotation.Generated(value = "io.swagger.codegen.languages.JavaClientCodegen", date = "2017-12-19T13:37:48.590Z")
public class DataSource {
  @SerializedName("fields")
  private List<DataSourceField> fields = new ArrayList<DataSourceField>();

  public DataSource fields(List<DataSourceField> fields) {
    this.fields = fields;
    return this;
  }

  public DataSource addFieldsItem(DataSourceField fieldsItem) {
    this.fields.add(fieldsItem);
    return this;
  }

   /**
   * Get fields
   * @return fields
  **/
  @ApiModelProperty(example = "null", required = true, value = "")
  public List<DataSourceField> getFields() {
    return fields;
  }

  public void setFields(List<DataSourceField> fields) {
    this.fields = fields;
  }


  @Override
  public boolean equals(java.lang.Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    DataSource dataSource = (DataSource) o;
    return Objects.equals(this.fields, dataSource.fields);
  }

  @Override
  public int hashCode() {
    return Objects.hash(fields);
  }


  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class DataSource {\n");
    
    sb.append("    fields: ").append(toIndentedString(fields)).append("\n");
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

