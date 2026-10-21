<#--
  Shared page frame for the CubeWorks-styled Keycloak pages (login.ftl,
  login-reset-password.ftl): grey page, centered white card, title on the left
  and the CW brand on the right (wireframe `#login-view`), then one alert slot.
  Styles: resources/css/login-page.css.

  @param title     card heading text
  @param errorText field-level error to show (wins over the page message), or ""
-->
<#macro page title errorText="">
<!DOCTYPE html>
<html lang="${(locale.currentLanguageTag)!'en'}">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="robots" content="noindex, nofollow">
  <title>${title} · CubeWorks HR</title>
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700&display=swap">
  <link rel="stylesheet" href="${url.resourcesPath}/css/login-page.css">
</head>
<body>
  <main class="cw-login">
    <div class="cw-card">
      <div class="cw-card-head">
        <h1 class="cw-title">${title}</h1>
        <div class="cw-brand" aria-label="CubeWorks HR">
          <span class="cw-logo" aria-hidden="true">CW</span>
          <span class="cw-brand-name">CubeWorks HR</span>
        </div>
      </div>

      <#if errorText?has_content>
        <div class="cw-alert cw-alert-error" role="alert">${kcSanitize(errorText)?no_esc}</div>
      <#elseif message?has_content>
        <div class="cw-alert cw-alert-${message.type}" role="alert">${kcSanitize(message.summary)?no_esc}</div>
      </#if>

      <#nested>
    </div>
  </main>
</body>
</html>
</#macro>
