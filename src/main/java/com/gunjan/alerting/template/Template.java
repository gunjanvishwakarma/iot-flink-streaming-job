package com.gunjan.alerting.template;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.annotations.ApiModelProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Template
 */
@javax.annotation.Generated(value = "org.openapitools.codegen.languages.SpringCodegen", date = "2021-05-04T20:00:52.929+05:30[Asia/Kolkata]")

public class Template {
  @JsonProperty("id")
  private UUID id;

  @JsonProperty("name")
  private String name;

  @JsonProperty("deviceCount")
  private Integer deviceCount;

  /**
   * Gets or Sets deviceType
   */
  public enum DeviceTypeEnum {
    NETWORK_DEVICE("Network Device"),
    
    SENSOR("Sensor");

    private String value;

    DeviceTypeEnum(String value) {
      this.value = value;
    }

    @Override
    @JsonValue
    public String toString() {
      return String.valueOf(value);
    }

    @JsonCreator
    public static DeviceTypeEnum fromValue(String value) {
      for (DeviceTypeEnum b : DeviceTypeEnum.values()) {
        if (b.value.equals(value)) {
          return b;
        }
      }
      throw new IllegalArgumentException("Unexpected value '" + value + "'");
    }
  }

  @JsonProperty("deviceType")
  private DeviceTypeEnum deviceType;

  @JsonProperty("model")
  private String model;

  @JsonProperty("description")
  private String description;

  @JsonProperty("isDefault")
  private Boolean isDefault = false;

  @JsonProperty("devices")
  
  private List<UUID> devices = null;

  @JsonProperty("alerts")
  
  private List<Alert> alerts = null;

  @JsonProperty("modifiedTime")
  private Long modifiedTime;

  @JsonProperty("modifiedBy")
  private String modifiedBy;

  @JsonProperty("createdTime")
  private Long createdTime;

  @JsonProperty("createdBy")
  private String createdBy;

  public Template id(UUID id) {
    this.id = id;
    return this;
  }

  /**
   * Get id
   * @return id
  */
  @ApiModelProperty(value = "")

  

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public Template name(String name) {
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

  public Template deviceCount(Integer deviceCount) {
    this.deviceCount = deviceCount;
    return this;
  }

  /**
   * Get deviceCount
   * @return deviceCount
  */
  @ApiModelProperty(value = "")


  public Integer getDeviceCount() {
    return deviceCount;
  }

  public void setDeviceCount(Integer deviceCount) {
    this.deviceCount = deviceCount;
  }

  public Template deviceType(DeviceTypeEnum deviceType) {
    this.deviceType = deviceType;
    return this;
  }

  /**
   * Get deviceType
   * @return deviceType
  */
  @ApiModelProperty(value = "")


  public DeviceTypeEnum getDeviceType() {
    return deviceType;
  }

  public void setDeviceType(DeviceTypeEnum deviceType) {
    this.deviceType = deviceType;
  }

  public Template model(String model) {
    this.model = model;
    return this;
  }

  /**
   * Get model
   * @return model
  */
  @ApiModelProperty(value = "")


  public String getModel() {
    return model;
  }

  public void setModel(String model) {
    this.model = model;
  }

  public Template description(String description) {
    this.description = description;
    return this;
  }

  /**
   * Get description
   * @return description
  */
  @ApiModelProperty(value = "")


  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public Template isDefault(Boolean isDefault) {
    this.isDefault = isDefault;
    return this;
  }

  /**
   * if true then template is default template other wise user defined template.
   * @return isDefault
  */
  @ApiModelProperty(value = "if true then template is default template other wise user defined template.")


  public Boolean getIsDefault() {
    return isDefault;
  }

  public void setIsDefault(Boolean isDefault) {
    this.isDefault = isDefault;
  }

  public Template devices(List<UUID> devices) {
    this.devices = devices;
    return this;
  }

  public Template addDevicesItem(UUID devicesItem) {
    if (this.devices == null) {
      this.devices = new ArrayList<>();
    }
    this.devices.add(devicesItem);
    return this;
  }

  /**
   * List of Devices
   * @return devices
  */
  @ApiModelProperty(value = "List of Devices")

  

  public List<UUID> getDevices() {
    return devices;
  }

  public void setDevices(List<UUID> devices) {
    this.devices = devices;
  }

  public Template alerts(List<Alert> alerts) {
    this.alerts = alerts;
    return this;
  }

  public Template addAlertsItem(Alert alertsItem) {
    if (this.alerts == null) {
      this.alerts = new ArrayList<>();
    }
    this.alerts.add(alertsItem);
    return this;
  }

  /**
   * List of Alerts.
   * @return alerts
  */
  @ApiModelProperty(value = "List of Alerts.")

  

  public List<Alert> getAlerts() {
    return alerts;
  }

  public void setAlerts(List<Alert> alerts) {
    this.alerts = alerts;
  }

  public Template modifiedTime(Long modifiedTime) {
    this.modifiedTime = modifiedTime;
    return this;
  }

  /**
   * The template object last updated time in unix epoch time. ex-1573483982768
   * @return modifiedTime
  */
  @ApiModelProperty(value = "The template object last updated time in unix epoch time. ex-1573483982768")


  public Long getModifiedTime() {
    return modifiedTime;
  }

  public void setModifiedTime(Long modifiedTime) {
    this.modifiedTime = modifiedTime;
  }

  public Template modifiedBy(String modifiedBy) {
    this.modifiedBy = modifiedBy;
    return this;
  }

  /**
   * The template object modified by the user
   * @return modifiedBy
  */
  @ApiModelProperty(value = "The template object modified by the user")


  public String getModifiedBy() {
    return modifiedBy;
  }

  public void setModifiedBy(String modifiedBy) {
    this.modifiedBy = modifiedBy;
  }

  public Template createdTime(Long createdTime) {
    this.createdTime = createdTime;
    return this;
  }

  /**
   * The template object last updated time in unix epoch time. ex-1573483982768
   * @return createdTime
  */
  @ApiModelProperty(value = "The template object last updated time in unix epoch time. ex-1573483982768")


  public Long getCreatedTime() {
    return createdTime;
  }

  public void setCreatedTime(Long createdTime) {
    this.createdTime = createdTime;
  }

  public Template createdBy(String createdBy) {
    this.createdBy = createdBy;
    return this;
  }

  /**
   * The template object created by the user
   * @return createdBy
  */
  @ApiModelProperty(value = "The template object created by the user")


  public String getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(String createdBy) {
    this.createdBy = createdBy;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Template template = (Template) o;
    return Objects.equals(this.id, template.id) &&
        Objects.equals(this.name, template.name) &&
        Objects.equals(this.deviceCount, template.deviceCount) &&
        Objects.equals(this.deviceType, template.deviceType) &&
        Objects.equals(this.model, template.model) &&
        Objects.equals(this.description, template.description) &&
        Objects.equals(this.isDefault, template.isDefault) &&
        Objects.equals(this.devices, template.devices) &&
        Objects.equals(this.alerts, template.alerts) &&
        Objects.equals(this.modifiedTime, template.modifiedTime) &&
        Objects.equals(this.modifiedBy, template.modifiedBy) &&
        Objects.equals(this.createdTime, template.createdTime) &&
        Objects.equals(this.createdBy, template.createdBy);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name, deviceCount, deviceType, model, description, isDefault, devices, alerts, modifiedTime, modifiedBy, createdTime, createdBy);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class Template {\n");
    
    sb.append("    id: ").append(toIndentedString(id)).append("\n");
    sb.append("    name: ").append(toIndentedString(name)).append("\n");
    sb.append("    deviceCount: ").append(toIndentedString(deviceCount)).append("\n");
    sb.append("    deviceType: ").append(toIndentedString(deviceType)).append("\n");
    sb.append("    model: ").append(toIndentedString(model)).append("\n");
    sb.append("    description: ").append(toIndentedString(description)).append("\n");
    sb.append("    isDefault: ").append(toIndentedString(isDefault)).append("\n");
    sb.append("    devices: ").append(toIndentedString(devices)).append("\n");
    sb.append("    alerts: ").append(toIndentedString(alerts)).append("\n");
    sb.append("    modifiedTime: ").append(toIndentedString(modifiedTime)).append("\n");
    sb.append("    modifiedBy: ").append(toIndentedString(modifiedBy)).append("\n");
    sb.append("    createdTime: ").append(toIndentedString(createdTime)).append("\n");
    sb.append("    createdBy: ").append(toIndentedString(createdBy)).append("\n");
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

