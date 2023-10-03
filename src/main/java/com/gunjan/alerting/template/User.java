package com.gunjan.alerting.template;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;

import java.util.Objects;
import java.util.UUID;

public class User {
  @JsonProperty("iamUserId")
  private UUID iamUserId;

  @JsonProperty("username")
  private String username;

  @JsonProperty("email")
  private String email;

  public User iamUserId(UUID iamUserId) {
    this.iamUserId = iamUserId;
    return this;
  }

  /**
   * Get iamUserId
   * @return iamUserId
  */
  @ApiModelProperty(value = "")

  

  public UUID getIamUserId() {
    return iamUserId;
  }

  public void setIamUserId(UUID iamUserId) {
    this.iamUserId = iamUserId;
  }

  public User username(String username) {
    this.username = username;
    return this;
  }

  /**
   * Get username
   * @return username
  */
  @ApiModelProperty(value = "")


  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public User email(String email) {
    this.email = email;
    return this;
  }

  /**
   * Get email
   * @return email
  */
  @ApiModelProperty(value = "")


  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    User user = (User) o;
    return Objects.equals(this.iamUserId, user.iamUserId) &&
        Objects.equals(this.username, user.username) &&
        Objects.equals(this.email, user.email);
  }

  @Override
  public int hashCode() {
    return Objects.hash(iamUserId, username, email);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class User {\n");
    
    sb.append("    iamUserId: ").append(toIndentedString(iamUserId)).append("\n");
    sb.append("    username: ").append(toIndentedString(username)).append("\n");
    sb.append("    email: ").append(toIndentedString(email)).append("\n");
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

