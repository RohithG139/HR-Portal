<#--
  CubeWorks HR login page (S13, wireframe `#login-view`).

  Self-contained page instead of keycloak.v2's PatternFly layout: the wireframe puts
  the "Login" title and the CW brand on one row, draws labels on the input border
  and puts "Forgot password?" beside the button, which the parent template's fixed
  header/form/footer structure cannot produce. Form contract is unchanged from the
  parent login.ftl: same action URL, field names (username, password, credentialId),
  autocomplete hints, forgot-password link and error messages (messages_en.properties).
  Page frame lives in cw-layout.ftl (shared with login-reset-password.ftl).
-->
<#import "cw-layout.ftl" as cw>
<#assign hasError = messagesPerField.existsError('username','password')>
<#-- One combined error for wrong e-mail/password (WS-04) -->
<@cw.page title=msg("loginAccountTitle") errorText=hasError?then(messagesPerField.getFirstError('username','password'), "")>
  <#if realm.password>
    <form id="kc-form-login" action="${url.loginAction}" method="post" novalidate onsubmit="document.getElementById('kc-login').disabled = true; return true;">
      <#if !usernameHidden??>
        <fieldset class="cw-field<#if hasError> cw-field-error</#if>">
          <legend><label for="username"><#if !realm.loginWithEmailAllowed>${msg("username")}<#elseif !realm.registrationEmailAsUsername>${msg("usernameOrEmail")}<#else>${msg("email")}</#if><span class="cw-req" aria-hidden="true">*</span></label></legend>
          <input id="username" name="username" type="text" value="${(login.username)!''}" autocomplete="username" autofocus required aria-invalid="${hasError?c}">
        </fieldset>
      </#if>

      <fieldset class="cw-field<#if hasError> cw-field-error</#if>">
        <legend><label for="password">${msg("password")}<span class="cw-req" aria-hidden="true">*</span></label></legend>
        <input id="password" name="password" type="password" autocomplete="current-password" required <#if usernameHidden??>autofocus</#if> aria-invalid="${hasError?c}">
      </fieldset>

      <input type="hidden" id="id-hidden-input" name="credentialId" <#if auth.selectedCredential?has_content>value="${auth.selectedCredential}"</#if>>

      <div class="cw-actions">
        <#if realm.resetPasswordAllowed>
          <a class="cw-link" href="${url.loginResetCredentialsUrl}">${msg("doForgotPassword")}</a>
        <#else>
          <span></span>
        </#if>
        <button id="kc-login" name="login" type="submit" class="cw-button">${msg("doLogIn")}</button>
      </div>
    </form>
  </#if>
</@cw.page>
