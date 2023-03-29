package com.example.vaadinFlow;

import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.messages.MessageInput;
import com.vaadin.flow.component.messages.MessageList;
import com.vaadin.flow.component.messages.MessageListItem;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.Command;
import com.vaadin.flow.spring.security.AuthenticationContext;
import com.vaadin.flow.spring.security.VaadinWebSecurity;
import jakarta.annotation.security.PermitAll;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Set;

@SpringBootApplication
@Push
public class VaadinFlowApplication implements AppShellConfigurator {

    public static void main(String[] args) {
        SpringApplication.run(VaadinFlowApplication.class, args);
    }

}

@Route("")
@PermitAll
class ChatView extends VerticalLayout {
    ChatView(ChatService chatService) {
        var messageList = new MessageList();
        var messageInput = new MessageInput();
        setSizeFull();
        add(messageList, messageInput);
        expand(messageList);
        messageInput.setWidthFull();

        chatService.join().subscribe(message -> {
            var nl = new ArrayList<>(messageList.getItems());
            nl.add(new MessageListItem(message.text(), message.time(), message.userName()));
            getUI().ifPresent(ui -> ui.access((Command) () -> messageList.setItems(nl)));
        });

        messageInput.addSubmitListener(event -> chatService.add(event.getValue()));
    }
}

@Route("login")
class LoginView extends VerticalLayout {

    LoginView() {
        var form = new LoginForm();
        form.setAction("login");
        add(form);
    }
}

@Configuration
class SecurityConfiguration extends VaadinWebSecurity {

    @Override
    protected void configure(HttpSecurity httpSecurity) throws Exception {
        super.configure(httpSecurity);
        setLoginView(httpSecurity, LoginView.class);
    }

    @Bean
    UserDetailsManager userDetailsManager() {
        var users = Set.of("Dulan", "Waruni").stream().map(name -> User.withDefaultPasswordEncoder().username(name).password("12345").roles("USER").build()).toList();
        return new InMemoryUserDetailsManager(users);
    }

}

record Message(String userName, String text, Instant time) {
}

@Service
class ChatService {
    private final Sinks.Many<Message> messages = Sinks.many().multicast().directBestEffort();

    private final Flux<Message> messageFlux = messages.asFlux();

    private final AuthenticationContext authenticationContext;

    ChatService(AuthenticationContext authenticationContext) {
        this.authenticationContext = authenticationContext;
    }

    Flux<Message> join() {
        return this.messageFlux;
    }

    void add(String message) {
        var username = this.authenticationContext.getPrincipalName().orElse("Anonymous");
        this.messages.tryEmitNext(new Message(username, message, Instant.now()));
    }
}
