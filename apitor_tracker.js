const https = require('https');
const crypto = require('crypto');
const { error } = require('console');

//TODO: Uncomment the line below if you haven't set up environment variables in your project yet.
// require('dotenv').config();



/**
 *  APITOR EXPRESS.JS INTERCEPTOR:
 *  Add this to your express.js project  and set 
 *        APITOR_PROJECT_TOKEN in env variables
 *        to the PROJECT TOKEN you received from APITOR
 *  IMPORT apitorTracker()
 *  Enable it globally in your express application using app.use(apitorTracker())
 */

//TODO: ADD your APITRO_PROJECT_TOKEN in environment variables
//TODO: MOUNT globally in you express.js application


function apitorTracker() {
    const apitorUrl = 'https://apitor-backend.onrender.com/aggregator';

    

    return function (req, res, next) {
        const startTime = process.hrtime();
        const createdAt = new Date().toISOString();
        const project_token = process.env.APITOR_PROJECT_TOKEN
     
        if (!project_token) {
            return next();
        }
      
    
        
        
        res.on('finish', () => {
            const diff = process.hrtime(startTime);
            
            const latencyInMs = (diff[0] * 1e3 + diff[1] / 1e6);

            
            let ipAddress = req.headers['x-forwarded-for'] || req.socket.remoteAddress;
            if (ipAddress && ipAddress.includes(',')) {
                ipAddress = ipAddress.split(',')[0].trim(); 
            }

            const requestId = req.headers['x-request-id'] || crypto.randomUUID();
            const endpointPath = req.originalUrl || req.url;
            const httpMethod = req.method;
            const statusCode = res.statusCode;



            
            let latitude = 0.0;
            let longitude = 0.0;
            if (req.headers['x-latitude']) latitude = parseFloat(req.headers['x-latitude']) || 0.0;
            if (req.headers['x-longitude']) longitude = parseFloat(req.headers['x-longitude']) || 0.0;

            let payloadSize =0;
            const contentLengthHeader = res.getHeader('content-length');
            if(contentLengthHeader){
                payloadSize= parseInt(contentLengthHeader , 10) || 0;
            }
            
            
            process.nextTick(() => {

                const metricsPayload = JSON.stringify({
                    ipAddress,
                    requestId,
                    endpointPath,
                    httpMethod,
                    statusCode,
                    latency: latencyInMs,
                    payloadSize, 
                    createdAt,
                    latitude,
                    longitude
                });

                const url = new URL(apitorUrl);
                const options = {
                    hostname: url.hostname,
                    path: url.pathname,
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'X-Project-Token': project_token,
                    },
                    timeout: 5000 
                };

    
                const clientRequest = https.request(options);
                
                clientRequest.on('error', (err) => {
                    // TODO: you can remove this logging if you want
                    console.error("APITOR LOGGIN ERROR: " + err.message);
                   
                });

                clientRequest.write(metricsPayload);
                clientRequest.end();
            });
        });

        next();
    };
}

module.exports = apitorTracker;