package com.gunjan.template;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Alert
 */
@javax.annotation.Generated(value = "org.openapitools.codegen.languages.SpringCodegen", date = "2021-05-04T20:00:52.929+05:30[Asia/Kolkata]")

public class Alert {
  @JsonProperty("id")
  private String id;

  @JsonProperty("name")
  private String name;

  @JsonProperty("alertNotification")
  private AlertNotification alertNotification;

  @JsonProperty("additionalAttributes")
  
  private Map<String, String> additionalAttributes = null;

  public Alert id(String id) {
    this.id = id;
    return this;
  }

  /**
   * Get id
   * @return id
  */
  @ApiModelProperty(value = "")


  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public Alert name(String name) {
    this.name = name;
    return this;
  }

  /**
   * Get name
   * @return name
  */
  @ApiModelProperty(value = "")


  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Alert alertNotification(AlertNotification alertNotification) {
    this.alertNotification = alertNotification;
    return this;
  }

  /**
   * Get alertNotification
   * @return alertNotification
  */
  @ApiModelProperty(value = "")

  

  public AlertNotification getAlertNotification() {
    return alertNotification;
  }

  public void setAlertNotification(AlertNotification alertNotification) {
    this.alertNotification = alertNotification;
  }

  public Alert additionalAttributes(Map<String, String> additionalAttributes) {
    this.additionalAttributes = additionalAttributes;
    return this;
  }

  public Alert putAdditionalAttributesItem(String key, String additionalAttributesItem) {
    if (this.additionalAttributes == null) {
      this.additionalAttributes = new HashMap<>();
    }
    this.additionalAttributes.put(key, additionalAttributesItem);
    return this;
  }

  /**
   * Get additionalAttributes
   * @return additionalAttributes
  */
  @ApiModelProperty(value = "")


  public Map<String, String> getAdditionalAttributes() {
    return additionalAttributes;
  }

  public void setAdditionalAttributes(Map<String, String> additionalAttributes) {
    this.additionalAttributes = additionalAttributes;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Alert alert = (Alert) o;
    return Objects.equals(this.id, alert.id) &&
        Objects.equals(this.name, alert.name) &&
        Objects.equals(this.alertNotification, alert.alertNotification) &&
        Objects.equals(this.additionalAttributes, alert.additionalAttributes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name, alertNotification, additionalAttributes);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class Alert {\n");
    
    sb.append("    id: ").append(toIndentedString(id)).append("\n");
    sb.append("    name: ").append(toIndentedString(name)).append("\n");
    sb.append("    alertNotification: ").append(toIndentedString(alertNotification)).append("\n");
    sb.append("    additionalAttributes: ").append(toIndentedString(additionalAttributes)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private String toIndentedString(Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }
}

