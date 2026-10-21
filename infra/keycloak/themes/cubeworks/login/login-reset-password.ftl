<#--
  CubeWorks HR "Forgot password?" page, styled like login.ftl (no wireframe exists
  for this screen; FRD-00 OPEN-LOGIN). Form contract matches keycloak.v2's
  login-reset-password.ftl: same action URL, "username" field prefilled from
  auth.attemptedUsername, back-to-login link. After submit Keycloak returns to the
  login page with emailSentMessage ("If this e-mail address is known...").
-->
<#import "cw-layout.ftl" as cw>
<#assign hasError = messagesPerField.existsError('username')>
<@cw.page title=msg("emailForgotTitle") errorText=hasError?then(messagesPerField.get('username'), "")>
  <p class="cw-intro"><#if realm.duplicateEmailsAllowed>${msg("emailInstructionUsername")}<#else>${msg("emailInstruction")}</#if></p>

  <form id="kc-reset-password-form" action="${url.loginAction}" method="post" novalidate onsubmit="document.getElementById('kc-reset').disabled = true; return true;">
    <fieldset class="cw-field<#if hasError> cw-field-error</#if>">
      <legend><label for="username"><#if !realm.loginWithEmailAllowed>${msg("username")}<#elseif !realm.registrationEmailAsUsername>${msg("usernameOrEmail")}<#else>${msg("email")}</#if><span class="cw-req" aria-hidden="true">*</span></label></legend>
      <input id="username" name="username" type="text" value="${(auth.attemptedUsername)!''}" autocomplete="username" autofocus required aria-invalid="${hasError?c}">
    </fieldset>

    <div class="cw-actions">
      <a class="cw-link" href="${url.loginUrl}">${msg("backToLogin")}</a>
      <button id="kc-reset" type="submit" class="cw-button">${msg("cwSendResetLink")}</button>
    </div>
  </form>
</@cw.page>
