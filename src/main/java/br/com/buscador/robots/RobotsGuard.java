package br.com.buscador.robots;

public class RobotsGuard {

    private final RobotsRules rules;

    public RobotsGuard(RobotsRules rules) {
        this.rules = rules;
    }

    public boolean isAllowed(String url) {
        return matchedFragment(url) == null;
    }

    public void ensureAllowed(String url) {
        String matched = matchedFragment(url);
        if (matched != null) {
            throw new DisallowedUrlException(
                    "URL proibida pelo robots.txt (trecho \"" + matched + "\"): " + url);
        }
    }

    private String matchedFragment(String url) {
        String urlLowerCase = url.toLowerCase();
        for (String fragment : rules.disallowedFragments()) {
            if (urlLowerCase.contains(fragment)) {
                return fragment;
            }
        }
        return null;
    }
}
