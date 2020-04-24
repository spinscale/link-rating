package de.spinscale.linkrating;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.context.SecurityContextHolder;

@Configuration
@EnableWebSecurity
public class WebSecurityConfig extends WebSecurityConfigurerAdapter  {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
                // add logout redirect to '/'
                .logout(l -> l
                        .logoutSuccessUrl("/").permitAll()
                        .addLogoutHandler((request, response, authentication) -> {
                            request.getSession().invalidate();
                            SecurityContextHolder.getContext().setAuthentication(null);
                        }).permitAll()
                )
                // authorize request with exceptions
                .authorizeRequests()
                .antMatchers("/", "/img/**", "/oauth2/**", "/logout", "/link/**").permitAll()
                .anyRequest().authenticated()
                .and().logout().permitAll()
                .and().oauth2Login().permitAll();
    }

}
