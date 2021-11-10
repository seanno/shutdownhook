const AWS = require('aws-sdk');
const sqs = new AWS.SQS();

exports.handler = async (event, context) => {
    
    console.log('Received event:', JSON.stringify(event, null, 2));

    var body = {
        'screenName': event.queryStringParameters.screenName,
        'screenSetting': event.queryStringParameters.settingName,
        'source': 'labmda'
    }; 
        
    var msg = { 
        MessageBody: JSON.stringify(body),
        QueueUrl: 'https://sqs.us-west-2.amazonaws.com/413499603360/shutdown_home_queue'
    };
    
    var statusCode = '200';
    var statusMsg = 'OK';
    
    await sqs.sendMessage(msg, function(err, data) {
        if (err) {
            statusCode = '500';
            statusMsg = err;
        }
    }).promise();

    return {
        'status': statusCode,
        'statusDescription': statusMsg
    };
};
