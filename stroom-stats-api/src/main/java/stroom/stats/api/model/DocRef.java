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

/**
 * The query terms for the search
 */
@ApiModel(description = "The query terms for the search")
@javax.annotation.Generated(value = "io.swagger.codegen.languages.JavaClientCodegen", date = "2017-12-19T13:37:48.590Z")
public class DocRef {
  @SerializedName("type")
  private String type = null;

  @SerializedName("id")
  private Long id = null;

  @SerializedName("uuid")
  private String uuid = null;

  @SerializedName("name")
  private String name = null;

  public DocRef type(String type) {
    this.type = type;
    return this;
  }

   /**
   * The type of the 'document' that this DocRef refers to
   * @return type
  **/
  @ApiModelProperty(example = "StroomStatsStore", required = true, value = "The type of the 'document' that this DocRef refers to")
  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public DocRef id(Long id) {
    this.id = id;
    return this;
  }

   /**
   * DEPRECATED
   * @return id
  **/
  @ApiModelProperty(example = "null", value = "DEPRECATED")
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public DocRef uuid(String uuid) {
    this.uuid = uuid;
    return this;
  }

   /**
   * The unique identifier for this 'document'
   * @return uuid
  **/
  @ApiModelProperty(example = "9f6184b4-bd78-48bc-b0cd-6e51a357f6a6", required = true, value = "The unique identifier for this 'document'")
  public String getUuid() {
    return uuid;
  }

  public void setUuid(String uuid) {
    this.uuid = uuid;
  }

  public DocRef name(String name) {
    this.name = name;
    return this;
  }

   /**
   * The name for the data source
   * @return name
  **/
  @ApiModelProperty(example = "MyStatistic", required = true, value = "The name for the data source")
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }


  @Override
  public boolean equals(java.lang.Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    DocRef docRef = (DocRef) o;
    return Objects.equals(this.type, docRef.type) &&
        Objects.equals(this.id, docRef.id) &&
        Objects.equals(this.uuid, docRef.uuid) &&
        Objects.equals(this.name, docRef.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, id, uuid, name);
  }


  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class DocRef {\n");
    
    sb.append("    type: ").append(toIndentedString(type)).append("\n");
    sb.append("    id: ").append(toIndentedString(id)).append("\n");
    sb.append("    uuid: ").append(toIndentedString(uuid)).append("\n");
    sb.append("    name: ").append(toIndentedString(name)).append("\n");
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
