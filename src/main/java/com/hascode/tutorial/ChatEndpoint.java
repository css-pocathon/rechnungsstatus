package com.hascode.tutorial;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.apache.http.HttpEntity;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import javax.websocket.EncodeException;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ServerEndpoint(value = "/chat/{user}/{room}", encoders = ChatMessageEncoder.class, decoders = ChatMessageDecoder.class)
public class ChatEndpoint {
    private final Logger log = Logger.getLogger(getClass().getName());

    private boolean on = true;
    private boolean p_on = true;

    private final static String BOT = "CSS Bot";

    @OnOpen
    public void open(final Session session, @PathParam("user") final String user, @PathParam("room") final String room) {
        log.info("session openend and bound to room: " + room);

        session.getUserProperties().put("user", user);
        session.getUserProperties().put("room", room);
        if (user.toLowerCase().equals("support")) {
            session.getUserProperties().put("bot", "false");
            session.getUserProperties().put("active", "false");
        } else {
            session.getUserProperties().put("bot", "true");
            session.getUserProperties().put("active", "true");
            session.getUserProperties().put("msg", new ArrayList<ChatMessage>());
        }

        String bot = (String) session.getUserProperties().get("bot");
        if (bot.equals("true")) {
            ChatMessage chatMessage = new ChatMessage();
            chatMessage.setSender(BOT);
            chatMessage.setMessage("Guten Tag Frau " + user);
            chatMessage.setReceived(new Date());

            try {
                session.getBasicRemote().sendObject(chatMessage);
            } catch (IOException | EncodeException e) {
                e.printStackTrace();
            }
        }
    }

    @OnMessage
    public void onMessage(final Session session, final ChatMessage chatMessage) {
        String room = (String) session.getUserProperties().get("room");

        Session usr = null;
        Session support = null;

        boolean exit = false;
        try {
            for (Session s : session.getOpenSessions()) {
                if (s.isOpen()
                        && room.equals(s.getUserProperties().get("room"))) {

                    String user = (String) s.getUserProperties().get("user");

                    String active = (String) s.getUserProperties().get("active");
                    String bot = (String) s.getUserProperties().get("bot");

                    if (user.toLowerCase().equals("support")) {
                        support = s;
                    }

                    if (!user.toLowerCase().equals("support")) {
                        usr = s;
                    }

                    if (active.equals("false")) {
                        continue;
                    }


                    if (chatMessage.getMessage().toLowerCase().equals("exit") && chatMessage.getSender().toLowerCase().equals("support")) {

                        exit = true;
                        continue;
                    }

                    chatMessage.setMessage(chatMessage.getMessage());
                    s.getBasicRemote().sendObject(chatMessage);

                    if (bot.equals("true")) {

                        List<ChatMessage> msg = (List<ChatMessage>) s.getUserProperties().get("msg");
                        msg.add(chatMessage);


                        ChatMessage m = new ChatMessage();
                        m.setMessage(handleUserMessage(chatMessage) +" " + user);
                        m.setReceived(new Date());
                        m.setSender(BOT);
                        s.getBasicRemote().sendObject(m);


                        msg.add(m);
                        s.getUserProperties().put("msg", msg);
                    }
                }
            }

            if (exit) {

                usr.getUserProperties().put("msg", new ArrayList<ChatMessage>());

                on = true;
                support.getUserProperties().put("active", "false");
                usr.getUserProperties().put("bot", "true");
                chatMessage.setMessage("Der Support hat den Chat verlassen");
                chatMessage.setSender(BOT);
                chatMessage.setReceived(new Date());
                usr.getBasicRemote().sendObject(chatMessage);

                ChatMessage bew = new ChatMessage();
                bew.setReceived(new Date());
                bew.setMessage("Wie bewerten Sie die Bearbeitung ihres Anliegens (gut, schlecht)?");
                bew.setSender(BOT);
                usr.getBasicRemote().sendObject(bew);

            }

            String ubot = (String) usr.getUserProperties().get("bot");
            if (!on && ubot.equals("true")) {


                ChatMessage ck = new ChatMessage();
                ck.setSender(BOT);
                ck.setReceived(new Date());
                String usrName = (String) usr.getUserProperties().get("user");
                ck.setMessage("Frau " + usrName + " hat ein Problem mit einer Rechnung.");
                support.getBasicRemote().sendObject(ck);

                ChatMessage cv = new ChatMessage();
                cv.setSender(BOT);
                cv.setReceived(new Date());
                cv.setMessage("Bisheriger Chatverlauf:");
                support.getBasicRemote().sendObject(cv);

                List<ChatMessage> msg1 = (List<ChatMessage>) usr.getUserProperties().get("msg");
                for (ChatMessage c : msg1) {
                    support.getBasicRemote().sendObject(c);
                }

                usr.getUserProperties().put("bot", "false");
                support.getUserProperties().put("active", "true");
                List<ChatMessage> msg = (List<ChatMessage>) usr.getUserProperties().get("msg");
                msg.clear();
                usr.getUserProperties().put("msg", msg);
            }


        } catch (IOException | EncodeException e) {
            log.log(Level.WARNING, "onMessage failed", e);
        }
    }


    private String handleUserMessage(ChatMessage chatMessage) {
        String response = callRechnungsstatusApp(chatMessage);

        JsonObject obj = new Gson().fromJson(response, JsonObject.class);
        String intent = obj.get("topScoringIntent").getAsJsonObject().get("intent").getAsString().toLowerCase();

        on = true;
        if (intent.equals("rechnungsstatus")) {
            return "Bitte geben Sie Ihre Rechnungsnummer ein.";
        } else if (intent.equals("bewertung")) {
            return "Vielen Dank für Ihre Bewertung.";
        } else if (intent.equals("rechnung")) {
            Pattern p = Pattern.compile("[0-9]+");
            Matcher m = p.matcher(chatMessage.getMessage());
            List<Integer> found = new ArrayList<>();
            while (m.find()) {
                found.add(Integer.parseInt(m.group()));
            }


            if (found.size() > 1) {
                return "Bitte geben sie nur eine Rechnungsnummer ein.";
            } else if (found.isEmpty()) {
                return "Bitte geben sie eine gültige Rechnungsnummer ein.";
            } else {

                if (found.get(0) == 12345) {
                    return "Rechnungsstatus: Bezahlt";

                }
                if (found.get(0) == 54321) {
                    return "Rechnungsstatus: Offen";

                } else {
                    on = false;
                    return "Ihre Rechnung wurde nicht gefunden, sie werden an einen Mitarbeiter weitergeleitet.";
                }


            }
        } else {
            return "Bitte geben Sie Ihre Rechnungsnummer ein um den Rechnungsstatus abzufragen";
        }

    }

    private String callRechnungsstatusApp(ChatMessage chatMessage) {


        try {
            HttpClient httpclient = HttpClients.createDefault();
            // The ID of a public sample LUIS app that recognizes intents for turning on and off lights
            String AppId = "49da9e5b-fc8e-489b-bfde-0c988eaac52d";

            // Add your subscription key
            String SubscriptionKey = "09b0903f75f24d76bd0f7567edc0dc3a";

            URIBuilder builder =
                    new URIBuilder("https://westus.api.cognitive.microsoft.com/luis/v2.0/apps/" + AppId + "?");

            builder.setParameter("q", chatMessage.getMessage());
            builder.setParameter("timezoneOffset", "0");
            builder.setParameter("verbose", "false");
            builder.setParameter("spellCheck", "false");
            builder.setParameter("staging", "false");

            URI uri = builder.build();
            HttpGet request = new HttpGet(uri);
            request.setHeader("Ocp-Apim-Subscription-Key", SubscriptionKey);

            org.apache.http.HttpResponse response = httpclient.execute(request);
            HttpEntity entity = response.getEntity();


            if (entity != null) {
                return EntityUtils.toString(entity);
            }
        } catch (Exception e) {


        }
        return "error";
    }

}
