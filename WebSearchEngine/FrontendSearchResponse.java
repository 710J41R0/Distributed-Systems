/*
 * Proyecto Final
 * Jairo Soto Yañez
 * 7CM3
 */

package com.mycompany.app;  

public class FrontendSearchResponse {
        private String requested;
        private String response;

        public FrontendSearchResponse(String requested, String response) {
            this.requested = requested;
            this.response = response;
        }

        public String getrequested() {
            return requested;
        }

        public String getresponse() {
            return response;
        }
}
