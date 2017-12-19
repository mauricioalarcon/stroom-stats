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
 * A unique key to identify the instance of the search by. This key is used to identify multiple requests for the same search when running in incremental mode.
 */
@ApiModel(description = "A unique key to identify the instance of the search by. This key is used to identify multiple requests for the same search when running in incremental mode.")
@javax.annotation.Generated(value = "io.swagger.codegen.languages.JavaClientCodegen", date = "2017-12-19T13:37:48.590Z")
public class QueryKey {
  @SerializedName("uuid")
  private String uuid = null;

  public QueryKey uuid(String uuid) {
    this.uuid = uuid;
    return this;
  }

   /**
   * The UUID that makes up the query key
   * @return uuid
  **/
  @ApiModelProperty(example = "7740bcd0-a49e-4c22-8540-044f85770716", required = true, value = "The UUID that makes up the query key")
  public String getUuid() {
    return uuid;
  }

  public void setUuid(String uuid) {
    this.uuid = uuid;
  }


  @Override
  public boolean equals(java.lang.Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    QueryKey queryKey = (QueryKey) o;
    return Objects.equals(this.uuid, queryKey.uuid);
  }

  @Override
  public int hashCode() {
    return Objects.hash(uuid);
  }


  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class QueryKey {\n");
    
    sb.append("    uuid: ").append(toIndentedString(uuid)).append("\n");
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

