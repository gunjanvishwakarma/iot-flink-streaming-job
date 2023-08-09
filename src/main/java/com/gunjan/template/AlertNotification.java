package com.gunjan.template;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * AlertNotification
 */
@javax.annotation.Generated(value = "org.openapitools.codegen.languages.SpringCodegen", date = "2021-05-04T20:00:52.929+05:30[Asia/Kolkata]")

public class AlertNotification {
  @JsonProperty("iamUsersToNotify")
  
  private List<User> iamUsersToNotify = null;

  @JsonProperty("emailsToNotify")
  
  private List<String> emailsToNotify = null;

  @JsonProperty("phoneNumbersToNotify")
  
  private List<String> phoneNumbersToNotify = null;

  public AlertNotification iamUsersToNotify(List<User> iamUsersToNotify) {
    this.iamUsersToNotify = iamUsersToNotify;
    return this;
  }

  public AlertNotification addIamUsersToNotifyItem(User iamUsersToNotifyItem) {
    if (this.iamUsersToNotify == null) {
      this.iamUsersToNotify = new ArrayList<>();
    }
    this.iamUsersToNotify.add(iamUsersToNotifyItem);
    return this;
  }

  /**
   * List of IAM Users to send Notification.
   * @return iamUsersToNotify
  */
  @ApiModelProperty(value = "List of IAM Users to send Notification.")

  

  public List<User> getIamUsersToNotify() {
    return iamUsersToNotify;
  }

  public void setIamUsersToNotify(List<User> iamUsersToNotify) {
    this.iamUsersToNotify = iamUsersToNotify;
  }

  public AlertNotification emailsToNotify(List<String> emailsToNotify) {
    this.emailsToNotify = emailsToNotify;
    return this;
  }

  public AlertNotification addEmailsToNotifyItem(String emailsToNotifyItem) {
    if (this.emailsToNotify == null) {
      this.emailsToNotify = new ArrayList<>();
    }
    this.emailsToNotify.add(emailsToNotifyItem);
    return this;
  }

  /**
   * List of Emails to send Notification
   * @return emailsToNotify
  */
  @ApiModelProperty(value = "List of Emails to send Notification")


  public List<String> getEmailsToNotify() {
    return emailsToNotify;
  }

  public void setEmailsToNotify(List<String> emailsToNotify) {
    this.emailsToNotify = emailsToNotify;
  }

  public AlertNotification phoneNumbersToNotify(List<String> phoneNumbersToNotify) {
    this.phoneNumbersToNotify = phoneNumbersToNotify;
    return this;
  }

  public AlertNotification addPhoneNumbersToNotifyItem(String phoneNumbersToNotifyItem) {
    if (this.phoneNumbersToNotify == null) {
      this.phoneNumbersToNotify = new ArrayList<>();
    }
    this.phoneNumbersToNotify.add(phoneNumbersToNotifyItem);
    return this;
  }

  /**
   * List of PhoneNumbers to send Notification
   * @return phoneNumbersToNotify
  */
  @ApiModelProperty(value = "List of PhoneNumbers to send Notification")


  public List<String> getPhoneNumbersToNotify() {
    return phoneNumbersToNotify;
  }

  public void setPhoneNumbersToNotify(List<String> phoneNumbersToNotify) {
    this.phoneNumbersToNotify = phoneNumbersToNotify;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    AlertNotification alertNotification = (AlertNotification) o;
    return Objects.equals(this.iamUsersToNotify, alertNotification.iamUsersToNotify) &&
        Objects.equals(this.emailsToNotify, alertNotification.emailsToNotify) &&
        Objects.equals(this.phoneNumbersToNotify, alertNotification.phoneNumbersToNotify);
  }

  @Override
  public int hashCode() {
    return Objects.hash(iamUsersToNotify, emailsToNotify, phoneNumbersToNotify);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class AlertNotification {\n");
    
    sb.append("    iamUsersToNotify: ").append(toIndentedString(iamUsersToNotify)).append("\n");
    sb.append("    emailsToNotify: ").append(toIndentedString(emailsToNotify)).append("\n");
    sb.append("    phoneNumbersToNotify: ").append(toIndentedString(phoneNumbersToNotify)).append("\n");
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

