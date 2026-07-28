Email assets for verification codes.

Drop the fruit-border header image here as:

    header.png

Both signup-code.html and login-code.html embed it inline (Content-ID "header",
referenced by <img src="cid:header">). If the file is missing, emails still send
but without the header image.

Templates use a single placeholder: {{code}} — replaced by MailService at send time.
