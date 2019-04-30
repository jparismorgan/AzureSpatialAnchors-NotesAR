// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.
using Microsoft.AspNetCore.Mvc;
using SharingService.Data;
using System.Collections.Generic;
using System.IO;
using System.Text;
using System.Threading.Tasks;

namespace SharingService.Controllers
{
    [Route("api/anchors")]
    [ApiController]
    public class AnchorsController : ControllerBase
    {
        private readonly IAnchorIdCache anchorIdCache;

        /// <summary>
        /// Initializes a new instance of the <see cref="AnchorsController"/> class.
        /// </summary>
        /// <param name="anchorIdCache">The anchor key cache.</param>
        public AnchorsController(IAnchorIdCache anchorIdCache)
        {
            this.anchorIdCache = anchorIdCache;
        }

        // GET api/anchors/build
        [HttpGet("{groupingKey}")]
        public async Task<ActionResult<string[]>> GetAsync(string groupingKey)
        {
            // Get the last anchor
            try
            {
                return await this.anchorIdCache.GetAnchorIdsAsync(groupingKey);
            }
            catch (KeyNotFoundException)
            {
                return this.NotFound();
            }
        }

        // POST api/anchors
        [HttpPost]
        public async Task<ActionResult> PostAsync()
        {
            string messageBody;
            using (StreamReader reader = new StreamReader(this.Request.Body, Encoding.UTF8))
            {
                messageBody = await reader.ReadToEndAsync();
            }

            if (string.IsNullOrWhiteSpace(messageBody))
            {
                return this.BadRequest();
            }

            string[] splitMessageBody = messageBody.Split("|");
            if (splitMessageBody.Length != 2)
            {
                return this.BadRequest();
            }
            string anchorId = splitMessageBody[0];
            string groupingKey = splitMessageBody[1];

            // Set the key
            await this.anchorIdCache.SetAnchorIdAsync(groupingKey, anchorId);

            return new EmptyResult();
        }
    }
}
